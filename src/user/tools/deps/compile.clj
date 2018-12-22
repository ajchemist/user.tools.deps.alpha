(ns user.tools.deps.compile
  (:refer-clojure :exclude [compile])
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [user.java.io.alpha :as io]
   [user.tools.deps.alpha :as u.deps]
   )
  (:import
   java.io.File
   java.net.URI
   java.net.URL
   java.net.URLClassLoader
   java.nio.file.Files
   java.nio.file.Path
   java.nio.file.Paths
   java.nio.file.attribute.FileAttribute
   ))


(set! *warn-on-reflection* true)


(def ^:dynamic *main-ns* 'user.tools.deps.compile)


(defn classpath->paths
  [classpath]
  (map
    io/path
    (-> classpath
      (str/trim)
      (str/split (re-pattern File/pathSeparator)))))


(defn paths->urls
  [paths]
  (map jio/as-url paths))


;;


(defn compile
  "AOT compile one or several Clojure namespace(s). Dependencies of the compiled namespaces are
  always AOT compiled too. Namespaces are loaded while beeing compiled so beware of side effects.
  - namespaces: A symbol or a collection of symbols naming one or several Clojure namespaces.
  - compile-path: The path to the directory where .class files are emitted. Default to `*compile-path*`.
  - compiler-options: A map with the same format than clojure.core/*compiler-options*."
  ([namespaces]
   (compile namespaces nil nil nil))
  ([namespaces compile-path classpath compiler-options]
   (let [compile-path   (or compile-path *compile-path*)
         compile-path   (io/mkdir compile-path)
         ;; We must ensure early that the compile-path exists otherwise the Clojure Compiler has issues compiling classes / loading classes. I'm not sure why exactly
         classpath      (or classpath (System/getProperty "java.class.path"))
         classpath-urls (->> classpath classpath->paths paths->urls (into-array URL))
         ;; classpath isolation
         classloader    (URLClassLoader. classpath-urls (.getParent (ClassLoader/getSystemClassLoader)))
         main-ns        *main-ns*
         main-class     (.loadClass classloader "clojure.main")
         main-method    (.getMethod main-class "main" (into-array Class [(Class/forName "[Ljava.lang.String;")]))
         t              (Thread.
                          (fn []
                            (.setContextClassLoader (Thread/currentThread) classloader)
                            (.invoke
                              main-method
                              nil
                              (into-array
                                Object
                                [(into-array String
                                   ["--main" (str main-ns)
                                    (pr-str namespaces) (str compile-path) (pr-str compiler-options)])]))))]
     (.start t)
     (.join t)
     (.close classloader))))


;;


(defn -main
  [namespaces compile-path compiler-options]
  (let [namespaces       (read-string namespaces)
        compiler-options (read-string compiler-options)]
    (try
      (binding [clojure.core/*loaded-libs* (ref (sorted-set))
                *compile-path*             (str compile-path)
                *compiler-options*         (or compiler-options *compiler-options*)]
        (run! clojure.core/compile namespaces))
      (clojure.core/shutdown-agents)
      (catch Throwable e
        (.printStackTrace e)))))


(set! *warn-on-reflection* false)


(comment


  (compile
    '[user.tools.deps.alpha]
    "target/classes"
    nil
    {:elide-meta [:doc :file :line :added]})


  )


;; Cleaning non project classes: https://dev.clojure.org/jira/browse/CLJ-322

;; Cleaning non project classes is not supported by user.tools.deps because:
;; Most of the time, libraries should be shipped without AOT. In the rare case when a library must be shipped AOT (let's say we don't want to ship the sources), directories can be removed programmatically, between build tasks. Shipping an application with AOT is a more common use case. In this case, AOT compiling dependencies is not an issue.

;; Compiling is done in a separate classloader because
;; - clojure.core/compile recursively compiles a namespace and its dependencies, unless the dependencies are already loaded. :reload-all does not help. Removing the AOT compiled files and recompiling results in a strange result: Source files are not reloaded, no .class file is produced. Using a separate classloader simulates a :reload-all for compile.
