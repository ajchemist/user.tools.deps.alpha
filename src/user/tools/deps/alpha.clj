(ns user.tools.deps.alpha
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [clojure.tools.deps.alpha :as deps]
   ))


(set! *warn-on-reflection* true)


(defn project-deps-edn
  []
  (let [{:keys [project-edn]} (deps/find-edn-maps)]
    (deps/merge-edns [{:mvn/repos mvn/standard-repos} project-edn])))


(defn make-classpath
  "- deps-map: Default to deps.edn map of the project (without merging the system-level and user-level deps.edn maps)"
  ([]
   (make-classpath nil))
  ([opts]
   (make-classpath nil opts))
  ([deps-map alias-kws]
   (let [merged   (or deps-map (project-deps-edn))
         args-map (deps/combine-aliases merged alias-kws)
         ;; lib-map  (deps/resolve-deps merged args-map)
         ]
     #_(deps/make-classpath lib-map (:paths merged) args-map)
     (-> (deps/calc-basis merged args-map) :classpath-roots deps/join-classpath))))


(defn get-jarpath
  ^String
  [lib coord]
  (let [path (get-in (deps/resolve-deps {:deps {lib coord}} {}) [lib :paths 0])]
    (when (str/ends-with? path ".jar")
      path)))


(set! *warn-on-reflection* false)


(comment
  (make-classpath)
  )
