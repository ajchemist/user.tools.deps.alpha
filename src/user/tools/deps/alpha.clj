(ns user.tools.deps.alpha
  (:require
   [clojure.string :as str]
   [clojure.java.io :as jio]
   [clojure.tools.deps.alpha.util.maven :as mvn]
   [clojure.tools.deps.alpha :as deps]
   [clojure.tools.deps.alpha.reader :as deps.reader]
   [clojure.tools.deps.alpha.script.make-classpath :as deps.make-classpath]
   ))


(set! *warn-on-reflection* true)


(defn- -deps-map
  []
  (update (deps.reader/slurp-deps "deps.edn")
    :mvn/repos #(merge %2 %) mvn/standard-repos))


(defn deps-map [] (-deps-map))


(defn make-classpath
  "- deps-map: Default to deps.edn map of the project (without merging the system-level and user-level deps.edn maps)"
  ([]
   (make-classpath nil))
  ([opts]
   (make-classpath nil opts))
  ([deps-map {:keys [resolve-aliases makecp-aliases aliases] :as opts}]
   (let [deps-map (or deps-map (-deps-map))]
     (:classpath (deps.make-classpath/create-classpath deps-map opts)))))


(defn get-jarpath
  ^String
  [lib coord]
  (let [path (get-in (deps/resolve-deps {:deps {lib coord}} {}) [lib :paths 0])]
    (when (str/ends-with? path ".jar")
      path)))


(set! *warn-on-reflection* false)


(comment
  (deps.reader/read-deps [])
  (deps.reader/read-deps (rest (deps.reader/default-deps)))
  (deps.make-classpath/create-classpath {:deps {}} {})
  (deps.make-classpath/create-classpath (deps.reader/read-deps []) {})
  (deps.make-classpath/create-classpath '{:deps {org.clojure/clojure {:mvn/version "1.10.1"}}} {})
  )
