(ns user.tools.deps.cli.api
  (:require
   [user.tools.deps.maven.alpha :as maven]
   [user.tools.deps.clean :as clean]
   [user.tools.deps.javac :as javac]
   [user.tools.deps.compile :as compile]
   [user.tools.deps.jar :as jar]
   [user.tools.deps.deploy :as deploy]
   ))


(defn sync-pom
  "Public API for clojure -X usage"
  [options]
  (maven/sync-pom-x options))


(defn clean
  "Public API for clojure -X usage"
  [options]
  (clean/clean-x options))


(defn javac
  "Public API for clojure -X usage"
  [options]
  (javac/javac-x options))


(defn compile
  "Public API for clojure -X usage"
  [options]
  (compile/compile-x options))


(defn jar
  "Public API for clojure -X usage"
  [options]
  (jar/jar-x options))


(defn uber
  "Public API for clojure -X usage"
  [options]
  (jar/uber-x options))


(defn deploy
  "Public API for clojure -X usage"
  [options]
  (deploy/deploy-x options))


(defn package
  "Public API for clojure -X usage"
  [{:keys [sync-pom clean javac compile jar uber deploy]}]
  (when (map? sync-pom)
    (maven/sync-pom-x sync-pom))
  (when (map? clean)
    (clean/clean-x clean))
  (when (map? javac)
    (javac/javac-x javac))
  (when (map? compile)
    (compile/compile-x compile))
  (when (map? jar)
    (jar/jar-x jar))
  (when (map? uber)
    (jar/uber-x uber))
  (when (map? deploy)
    (deploy/deploy-x deploy)))
