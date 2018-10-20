(ns user.tools.deps.util.shell
  (:refer-clojure :exclude [flush read-line])
  (:require
   [clojure.java.io :as jio]
   )
  (:import
   java.io.InputStream
   java.io.OutputStream
   java.util.List
   java.util.concurrent.TimeUnit
   java.util.concurrent.TimeoutException
   ))


(set! *warn-on-reflection* true)


(defn proc
  "Spin off another process. Returns the process's input stream,
  output stream, and err stream as a map of :in, :out, and :err keys
  If passed the optional :dir and/or :env keyword options, the dir
  and enviroment will be set to what you specify. If you pass
  :verbose and it is true, commands will be printed. If it is set to
  :very, environment variables passed, dir, and the command will be
  printed. If passed the :clear-env keyword option, then the process
  will not inherit its environment from its parent process."
  [& args]
  (let [[cmd args] (split-with (complement keyword?) args)
        args       (apply hash-map args)
        builder    (ProcessBuilder. ^List cmd)
        env        (.environment builder)]
    (when (:clear-env args)
      (.clear env))
    (doseq [[k v] (:env args)]
      (.put env k v))
    (when-let [dir (:dir args)]
      (.directory builder (jio/file dir)))
    (when (:verbose args) (apply println cmd))
    (when (= :very (:verbose args))
      (when-let [env (:env args)] (prn env))
      (when-let [dir (:dir args)] (prn dir)))
    (when (:redirect-err args)
      (.redirectErrorStream builder true))
    (let [process (.start builder)]
      {:out     (.getInputStream process)
       :in      (.getOutputStream process)
       :err     (.getErrorStream process)
       :process process})))


(defn destroy
  "Destroy a process."
  [process]
  (.destroy ^Process (:process process)))


;; .waitFor returns the exit code. This makes this function useful for
;; both getting an exit code and stopping the thread until a process
;; terminates.
(defn exit-code
  "Waits for the process to terminate (blocking the thread) and returns
   the exit code. If timeout is passed, it is assumed to be milliseconds
   to wait for the process to exit. If it does not exit in time, it is
   killed (with or without fire)."
  ([process] (.waitFor ^Process (:process process)))
  ([process timeout]
     (try
       (deref (future (.waitFor ^Process (:process process))) timeout TimeUnit/MILLISECONDS)
       (catch Exception e
         (if (or (instance? TimeoutException e)
                 (instance? TimeoutException (.getCause e)))
           (do (destroy process)
               :timeout)
           (throw e))))))


(defn flush
  "Flush the output stream of a process."
  [process]
  (.flush ^OutputStream (:in process)))


(defn done
  "Close the process's output stream (sending EOF)."
  [proc]
  (.close ^OutputStream (:in proc)))


(defn stream-to
  "Stream :out or :err from a process to an ouput stream.
  Options passed are fed to clojure.java.io/copy. They are :encoding to
  set the encoding and :buffer-size to set the size of the buffer.
  :encoding defaults to UTF-8 and :buffer-size to 1024."
  [process from to & args]
  (apply jio/copy (process from) to args))


(defn feed-from
  "Feed to a process's input stream with optional. Options passed are
  fed to clojure.java.io/copy. They are :encoding to set the encoding
  and :buffer-size to set the size of the buffer. :encoding defaults to
  UTF-8 and :buffer-size to 1024. If :flush is specified and is false,
  the process will be flushed after writing."
  [process from & {flush? :flush :or {flush? true} :as all}]
  (apply jio/copy from (:in process) all)
  (when flush? (flush process)))


(defn stream-to-string
  "Streams the output of the process to a string and returns it."
  [process from & args]
  (with-open [writer (java.io.StringWriter.)]
    (apply stream-to process from writer args)
    (str writer)))


;; The writer that Clojure wraps System/out in for *out* seems to buffer
;; things instead of writing them immediately. This wont work if you
;; really want to stream stuff, so we'll just skip it and throw our data
;; directly at System/out.
(defn stream-to-out
  "Streams the output of the process to System/out"
  [process from & args]
  (apply stream-to process from (System/out) args))


(defn feed-from-string
  "Feed the process some data from a string."
  [process s & args]
  (apply feed-from process (java.io.StringReader. s) args))


(defn read-line
  "Read a line from a process' :out or :err."
  [process from]
  (binding [*in* (jio/reader (from process))]
    (clojure.core/read-line)))


;;


(def ^:dynamic *sh-dir*
  "The directory to use as CWD for shell commands."
  nil)


(defn sh
  "Evaluate args as a shell command, asynchronously, and return a thunk which
  may be called to block on the exit status. Output from the shell is streamed
  to stdout and stderr as it is produced."
  [& args]
  (let [args (remove nil? args)]
    (assert (every? string? args))
    (let [opts (into [:redirect-err true] (when *sh-dir* [:dir *sh-dir*]))
          proc (apply proc (concat args opts))]
      (future (stream-to-out proc :out))
      #(exit-code proc))))


(defn dosh
  "Evaluates args as a shell command, blocking on completion and throwing an
  exception on non-zero exit status. Output from the shell is streamed to
  stdout and stderr as it is produced."
  [& args]
  (let [[arg]       args
        {:keys [error-msg]
         :or   {error-msg "Process execution error"}
         :as   opt} (if (map? arg) arg nil)
        args        (if (map? arg) (rest args) args)
        args        (remove nil? args)]
    (assert (every? string? args))
    (let [exit-code ((apply sh args))]
      (when-not (== 0 exit-code)
        (throw
          (ex-info error-msg
            {:exit-code exit-code
             :args      args}))))))


(set! *warn-on-reflection* false)


(comment
  (dosh "ls")
  (dosh {} "ls")
  )
