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
          :else (keyword "unknown")
        )))

; Resource Calculation

(defn calculate-default-session-limits
  ""
  [])

(defn calculate-adaptive-limits
  ""
  [])

(defn get-current-session-load
  ""
  []) ; Use wrap-try-catch function here

; Limit Enforcement

(defn can-create-session?
  ""
  [])

(defn suggest-session-config
  ""
  [])

(defn check-resource-health
  ""
  [])

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