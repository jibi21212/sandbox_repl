(ns org.example.gui-repl.backend.system
  (:require [org.example.gui-repl.backend.utils :as utils]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (com.sun.management OperatingSystemMXBean)
           [java.lang.management ManagementFactory]
           [java.io File]
           [java.util.concurrent TimeUnit]))

(def current-system-limits (atom nil))                      ; State atom for the current system limits

; Core System Detection

(defn get-runtime-info
  "Get basic JVM runtime Info"
  []
  (try
    (let [rt (Runtime/getRuntime)]
    {:available-processors (.availableProcessors rt)
     :max-memory (.maxMemory rt)
     :total-memory (.totalMemory rt)
     :free-memory (.freeMemory rt)})
    (catch Exception e
      {:available-processors nil
       :max-memory nil
       :total-memory nil
       :free-memory nil})))
; No need for an 'if instance? ...' safety check, try-catch is sufficient
; we call a static method that always returns a valid Runtime instance
; unless there is a catastrophic JVM failure and at that point there is nothing got be done

(defn get-system-memory-info
  "Get detailed system memory information beyond JVM heap"
  []
  (try
    (let [os-mxbean (ManagementFactory/getOperatingSystemMXBean)]
     (if (instance? OperatingSystemMXBean os-mxbean)
     (let [bean (cast OperatingSystemMXBean os-mxbean)
           total (.getTotalPhysicalMemorySize bean)
           free (.getFreePhysicalMemorySize bean)
           pressure (if (pos? total) (- 1.0 (/ (double free) total)) 0.0)]
    {:total-system-memory total
     :available-memory free
     :memory-pressure pressure})  ; Currently works for OpenJDK 22.02 and OracleJDK as far as I know, not sure about others
     ; fallback for other JVMs -> Just return null
     {:total-system-memory nil
      :available-memory nil
      :memory-pressure 0.0}
     ))
    (catch Exception e
      {:total-system-memory nil
       :available-memory nil
       :memory-pressure 0.0})))

(defn get-disk-space-info
  "Get available disk space for session storage"
  ([] (get-disk-space-info nil))                                  ; Maybe instead of nil we can give a default directory the user defines or have our own predefined one?
  ([directory]
   (try (let [d (or directory (utils/get-temp-dir))
         file (if (instance? File d)
                d                   ; If given an instance of the java.io.File class (an actual file), then use d as is
                (File. d))]         ; If given a file path (which  is a string), call the constructor on d
                                    ; This is so we can use all the java.io.File methods on the 'file' local variable to get the data we want
         (if (utils/file-exists? (.getPath file))
           {:total-space (.getTotalSpace file)
            :free-space (.getFreeSpace file)
            :usable-space (.getUsableSpace file)}
           {:total-space 0
            :free-space 0
            :usable-space 0}))
        (catch Exception e
          {:total-space 0
           :free-space 0
           :usable-space 0}))))



(defn detect-os-type
  "Detect operating system type for OS-specific optimizations (& eventually features?)"
  []
 (let [os-name (utils/get-system-property "os.name" "unknown")
        check-name (fn [name check-for] (str/includes? (str/lower-case name) check-for))]
      (cond
          (check-name os-name "windows") (keyword "windows")
          (check-name os-name "mac") (keyword "mac")
          (check-name os-name "linux") (keyword "linux")
          :else (keyword "unknown"))))

; Resource Calculation

(defn calculate-max-sessions
  "Helper function to help calculate max sessions based on CPU cores"
  [cores]
  (* 2 cores))

(defn calculate-memory-per-session
  "Helper function to help calculate memory per session"
  [total-memory max-sessions conservative-factor]
  (if (> max-sessions 0)
    (/ (* total-memory conservative-factor) max-sessions)
    0))

(defn calculate-cpu-priority-per-session
  "Helper function to help calculate CPU priority allocation per session"
  [max-sessions]
  (if (> max-sessions 0)
   (/ 1 max-sessions)
   0))
; By default, it will assign equally, but the idea is, in process_manager once we start using sessions

(defn calculate-load-factor
  "Helper function to help calculate adjusted load"
  [current-load]                                            ; A map returned from the 'get-current-system-load'
  (let [cpu-usage (get current-load :cpu-usage)
        memory-usage (get current-load :memory-usage)]
   (cond
    (or (> cpu-usage 0.8) (> memory-usage 0.8)) 0.5
    (or (> cpu-usage 0.6) (> memory-usage 0.6)) 0.75
    (and (> cpu-usage 0.3) (< memory-usage 0.3)) 1.2
    :else 1.0)))

(defn calculate-default-session-limits
  "Calculate recommended limits for REPL sessions based on detected resources"
  ([system-info] (calculate-default-session-limits system-info 0.25))
  ([system-info conservative-factor]                                             ; a deep merged map of the detection functions above
  (let [total-memory (get system-info :max-memory)
        cpu-cores (get system-info :available-processors)
        max-sessions (calculate-max-sessions cpu-cores)]
   {:max-sessions max-sessions
    :memory-per-session (calculate-memory-per-session total-memory max-sessions conservative-factor)
    :cpu-per-session (calculate-cpu-priority-per-session max-sessions)})))

(defn calculate-adaptive-limits
  "Calculate and adjust values based on system limits and current system load"
  [base-limits]                                             ; Map from 'calculate-default-session-limits'
  (let [current-load (get-current-system-load)
        scale-factor (calculate-load-factor current-load)]
    {:max-sessions (* (get base-limits :max-sessions) scale-factor)
     :memory-per-session (* (get base-limits :memory-per-session) scale-factor)
     :cpu-per-session (get base-limits :cpu-per-session)
     }))

(defn get-current-system-load
  "Get current CPU and memory usage percentages"
  []
  (try
    (let [os-mxbean (ManagementFactory/getOperatingSystemMXBean)]
     (if (instance? OperatingSystemMXBean os-mxbean)
      (let [bean (cast OperatingSystemMXBean os-mxbean)
            used-cpu-percentage (let [cpu (.getSystemCpuLoad bean)] (if (>= cpu 0) cpu 0.0))
            free-memory (.getFreePhysicalMemorySize bean)
            total-memory (.getTotalPhysicalMemorySize bean)
            used-memory (- total-memory free-memory)
            used-memory-percentage (double (/ used-memory total-memory))
            ]
      {:cpu-usage used-cpu-percentage
       :memory-usage used-memory-percentage})
      {:cpu-usage 0
       :memory-usage 0}))
    (catch Exception e
      {:cpu-usage 0
       :memory-usage 0})))

; Limit Enforcement

(defn can-create-session?
  "Check if a new session can be created within current limits"
  [current-session-count system-limits]                     ; current-session-count is an int, system-limits likely refers to the atom
  (let [limits @system-limits
        current-load (get-current-system-load)]
    (and (< current-session-count (get limits :max-sessions 0))
         (< (get current-load :cpu-usage 0) 0.9)
         (< (get current-load :memory-usage 0) 0.9))))                    ; Return true only if all conditions are satisfied

(defn suggest-session-config
  "Suggest an optimal config for a new session"             ; Returns map with suggested-memory, timeout, priority-settings
  ; Use 'calculate-adaptive-limits' with language specific profiles, have defaults + user configs for a language
  [language session-type]
  :???) ; Should optimize for a language, this is more complex leave this for later

(defn check-resource-health
  "Monitor current resource usage and warn of issues"       ; Returns map with status, warnings, recommendations
  ; Uses all detection functions
  ; Use utils/log-info, utils/log-error, utils/log-debug as needed
  ; I assume this function orchestrates all the other detection functions and monitors
  ; Maybe there should be an agent here running in the background?
  ; Maybe this is where I deep-merge the maps that result from detection functions?
  ; I can have a helper function for this purpose
  []
  )

; Config and Persistence

(defn load-system-overrides
  ""
  [])

(defn save-detected-limits
  ""
  [])

(defn get-cached-limits
  ""
  [])

; Main API
; Use 'wrap-try-catch' function for these functions below
; This is where core.clj will call the above functions when communicating with Kotlin layer
; It's useful to have structured responses for Kotlin apperantly

(defn initialize-system-limits!
  ""
  [])

(defn get-current-limits
  ""
  [])

(defn refresh-system-limits!
  ""
  [])

; TODO: Add logging into all functions after I complete all of them for testing purposes
; I can opt to overload the function by giving a boolean which will make it log?