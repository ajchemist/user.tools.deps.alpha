(ns user.tools.deps.clean
  (:require
   [user.java.io.alpha :as io]
   )
  (:import
   java.nio.file.FileVisitOption
   java.nio.file.FileVisitResult
   java.nio.file.FileVisitor
   java.nio.file.Files
   java.nio.file.Path
   java.nio.file.Paths
   ))


(set! *warn-on-reflection* true)


(defn- make-file-visitor
  []
  (reify FileVisitor
    (postVisitDirectory [_ dir exception]
      (if (nil? exception)
        (do
          (Files/delete dir)
          FileVisitResult/CONTINUE)
        (throw exception)))
    (visitFile [_ path attrs]
      (Files/delete path)
      FileVisitResult/CONTINUE)
    (preVisitDirectory [_ dir attrs]
      FileVisitResult/CONTINUE)
    (visitFileFailed [_ file exception]
      (throw exception))))


(defn delete-recursively
  [path]
  (when (io/exists? path)
    (Files/walkFileTree (io/as-path path) (make-file-visitor))))


(defn sanity-check
  [path allow-outside-target?]
  (let [root-dir   (io/path (System/getProperty "user.dir"))
        target-dir (.resolve root-dir "target")]
    (when (not (io/parent-path? root-dir path))
      (throw (IllegalArgumentException. "Cannot delete a directory outside of project root")))
    (when (and
            (not allow-outside-target?)
            (not (io/same-directory? target-dir path))
            (not (io/parent-path? target-dir path)))
      (throw (IllegalArgumentException. "Cannot delete a directory outside of target-dir. Consider setting the \"allow-outside-target?\" option if you really want to delete this directory.")))))


(defn clean
  "Delete the target-dir. The directory to delete must not be outside of project root. By default, the directory to delete must either be the directory named \"target\" or must be inside the directory named \"target\". Setting the \"allow-outside-target?\" parameter to true makes deleting directories outside \"target\" possible."
  ([target-dir]
   (clean target-dir nil))
  ([target-dir {:keys [allow-outside-target?]}]
   (println "Clean" target-dir)
   (sanity-check target-dir allow-outside-target?)
   (delete-recursively target-dir)
   target-dir))


(defn clean-x
  [{:keys [dir options]}]
  (clean dir options))


(set! *warn-on-reflection* false)


(comment
  (clean "target")
  )


;; We do not forbid file overwriting in compile/javac/jar/bundle because
;; compile -> does not work with an existing file which is not a directory
;;         -> can only overwrite .class files
;; jar -> can only overwrite a .jar file
;; javac/bundle -> quite similar to compile
