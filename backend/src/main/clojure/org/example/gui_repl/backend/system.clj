(ns org.example.gui-repl.backend.system
  (:require [org.example.gui_repl.backend.utils :as utils]
            [clojure.java.io :as io])
  (:import [java.lang.management ManagementFactory MemoryMXBean]
           [java.io File]
           [java.util.concurrent TimeUnit]))

(def current-system-limits (atom nil))                      ; State atom for the current system limits

; Core System Detection

(defn get-runtime-info
  "Get basic JVM runtime Info"
  []
  (let
    [rt (Runtime/getRuntime)]
    {:available-processors (.availableProcessors rt)
     :max-memory (.maxMemory rt)
     :total-memory (.totalMemory rt)
     :free-memory (.freeMemory rt)}))

(defn get-system-memory-info
  "Get detailed system memory information beyond JVM heap"
  []
  (let [os-mxbean (java.lang.management.ManagementFactory/getOperatingSystemMXBean)
        bean (cast com.sun.management.OperatingSystemMXBean os-mxbean)
        total (.getTotalPhysicalMemorySize bean)
        free (.getFreePhysicalMemorySize bean)
        pressure (- 1.0 (/ (double free) total))]
    {:total-system-memory total
     :available-memory free
     :memory-pressure pressure}))

