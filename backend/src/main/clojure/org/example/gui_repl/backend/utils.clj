(ns org.example.gui_repl.backend.utils
    "Shared utilities and helper functions for the GUI REPL backend"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            )
  (:import (java.io Closeable File)
           (java.time LocalDateTime)
           (java.time.format DateTimeFormatter)
           (java.util ArrayList UUID)
           (java.util.concurrent TimeUnit)))

; ID Generation

(defn generate-session-id
      "Generate a unique session ID"
      []
      (str (UUID/randomUUID)))

; Timestamp generation

(defn generate-timestamp
      "Generate current time stamp in ms"
      []
      (System/currentTimeMillis))

; Java Interop Helper

(defn clj-map->java-map
      "Convert Clojure map to Java map for interop"
      [clj-map]
      (java.util.HashMap. clj-map)) ; Check why this is a warning and if I have to worry

(defn java-map->clj-map
      "Convert Java map to Clojure map"
      [java-map]
      (into {} java-map))

(defn clj-seq->java-map
      "Convert Clojure sequence to Java list"
      [clj-seq]
      (ArrayList clj-seq))

(defn keyword-keys->string-keys
      "Convert map with keyword keys to string keys for Java interop"
      [m]
      (into {} (map (fn [[k v]] [(name k) v]) m)))

(defn string-keys->keyword-keys
      "Convert map with string keys to keyword keys"
      [m]
      (into {} (map (fn [[k v]] [(keyword k) v]) m)))

; Error Handling

(defn success-response
      "Create a successful response map"
      [data]
      {:status :success
       :data data
       :timestamp (generate-timestamp)})

(defn error-response
      "Create an error response map"
      ([message]
       (error-response message nil))
      ([message error-code]
       {:status :error
        :message message
        :error-code error-code
        :timestamp (generate-timestamp)}))

(defn exception-response
      "Create an error response from an exception"
      [^Exception e]
      {:status :error
       :message (.getMessage e)
       :exception-type (.getSimpleName (.getClass e))
       :timestamp (generate-timestamp)})

(defn wrap-try-catch
      "Wrap a function call in a try-catch, returning an error response on an exception"
      [f & args]
      (try
        (success-response (apply f args))
        (catch Exception e
          (exception-response e))))

; String utils

(defn safe-trim
      "Safely trim a string, handling nil values"
      [s]
      (when s (str/trim s)))

(defn non-empty-string?
      "Check if string is non-empty after trimming"
      [s]
      (and s (not (str/blank? (str/trim s)))))

(defn parse-int-safe
      "Safely parse integer, returning default on failure"
      ([s] (parse-int-safe s nil))
      ([s default]
       (try
         (Integer/parseInt (str/trim s))
         (catch Exception _
           default))))

(defn parse-float-safe
      "Safely parse float, returning default on failure"
      ([s] (parse-float-safe s nil))
      ([s default]
       (try
         (Float/parseFloat (str/trim s))
         (catch Exception _
           default))))

; File System Utils

(defn ensure-directory
      "Ensure directory path exists, create if it doesn't"
      [path]
      (let [dir (io/file path)]
           (when-not (.exists dir)
                     (.mkdirs dir))
           dir))

(defn file-exists?
      "Check if file exists"
      [path]
      (-> path io/file .exists))

(defn delete-file-safe
      "Safely delete a file, returns true if successful"
      [path]
      (try
        (-> path io/file .delete)
        (catch Exception _
          false)))


(defn get-temp-dir
      "Get system temporary directory"
      []
      (System/getProperty "java.io.tmpdir"))

(defn create-temp-file
      "Create a temporary file with given prefix & suffix"
      ([prefix suffix]
       (File/createTempFile prefix suffix))
      ([prefix suffix directory]
       (File/createTempFile prefix suffix (io/file directory))))

; Collection Utils

(defn find-first
      "Find first item in collection matching predicate"
      [pred coll]
      (first (filter pred coll)))

(defn map-values
      "Apply function to all values in a map"
      [f m]
      (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn map-keys
      "Apply function to all keys in a map"
      [f m]
      (into {} (map (fn [[k v]] [(f k) v]) m)))

(defn remove-nil-values
      "Remove entries with nil values from map"
      [m]
      (into {} (remove (comp nil? second) m)))

(defn deep-merge
      "Deep merge maps"
      [& maps]
      (apply merge-with
             (fn [x y]
                 (if (and (map? x) (map? y))
                   (deep-merge x y)
                   y))
             maps))

; Time Utils

(defn current-time-iso
      "Get current time in ISO format"
      []
      (.format (LocalDateTime/now)
               (DateTimeFormatter/ISO_LOCAL_DATE_TIME)))    ; Check why this is a warning and if I have to worry

(defn millis->seconds
      "Convert milliseconds to seconds"
      [millis]
      (/ millis 1000.0))

(defn timeout-future
      "Create a future that times out after specified milliseconds"
      [timeout-ms f]
      (let [future (future (f))
            timeout-future (future
                             (Thread/sleep timeout-ms) ; Check why this is a warning and if I have to worry
                             ::timeout)]
           (first (filter #(not= % ::timeout)
                          (map deref [future timeout-future])))))

; Process Utils

(defn build-process-command
      "Build process command from base command and arguments"
      [base-cmd args]
      (into [base-cmd] args))

(defn process-alive?
      "Check if a process is still alive"
      [^Process process] ; ^ is a metadata marker, look more into this later?
      (when process
            (.isAlive process)))

(defn kill-process-safe
      "Safely kill a process"
      [^Process process]
      (when (and process (process-alive? process))
            (try
              (.destroy process) ; it thinks I am using .destroy from Thread groups, but I am doing it on a Process, IDE is mistaken, find a way to clarify this to the IDE later
              (when-not (.waitFor process 3 TimeUnit/SECONDS)
                        (.destroyForcibly process))
              true
              (catch Exception _
                false))))

; Validation Utils

(defn valid-session-id?
      "Validate session ID format"
      [session-id]
      (and (string? session-id)
           (non-empty-string? session-id)
           (re-matches #"^[a-fA-F0-9-]{36}$" session-id)))

(defn valid-language?
      "Validate language name"
      [language]
      (and (string? language)
           (non-empty-string? language)
           (re-matches #"^[a-zA-Z][a-zA-Z0-9_-]*$" language))) ; For now just leave checking for a language like this, see if there is a better way later?

(defn valid-port?
      "Validate port number"
      [port]
      (and (integer? port)
           (>= port 1)
           (<= port 65535))) ; Check why 65535 later

; Logging Utils

(defn log-info
      "Simple info logging"
      [message & args]
      (println (str "[INFO] " (current-time-iso) " - "
                    (apply format message args))))

(defn log-error
      "Simple error logging"
      [message & args]
      (println (str "[ERROR] " (current-time-iso) " - "
                    (apply format message args))))

(defn log-debug
      "Simple debug logging"
      [message & args]
      (when (System/getProperty "gui.repl.debug")
            (println (str "[DEBUG] " (current-time-iso) " - "
                          (apply format message args)))))

; Config Utils

(defn get-user-home
      "Get user home directory"
      []
      (System/getProperty "user.home"))

(defn get-gui-repl-config-dir
      "Get GUI REPL config directory"
      []
      (str (get-user-home) "./gui-repl"))

(defn get-system-property
      "Get system property with default value"
      [property default]
      (System/getProperty property default))

; Resource Management

(defmacro with-resource
          "Execute body with resource, ensuring cleanup"
          [binding & body]
          `(let ~binding
                (try
                  ~@body
                  (finally
                    (when-let [resource# ~(first binding)]
                              (when (instance? Closeable resource#)
                                    (.close resource#))))))) ; Look into this macro, find out what it actually does

; Development Utils

(defn spy
      "Util to print and return value"
      ([x] (spy "DEBUG" x))
      ([label x]
       (log-debug  "%s: %s" label (pr-str x))
       x)) ; Okay but what does this actually mean?

(defn benchmark
      "Simple benchmarking util"
      [label f]
      (let [start (System/nanoTime)
            result (f)
            duration (/ (- (System/nanoTime) start) 1000000.0)]
           (log-info "%s took %.2f ms" label duration)
           result))

