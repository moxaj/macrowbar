(merge-env!
  :resource-paths #{"src/cljc"}
  :dependencies   '[[org.clojure/clojure       "1.9.0-alpha17" :scope "provided"]
                    [org.clojure/clojurescript "1.9.908"       :scope "provided"]]
  :repositories   [["clojars" {:url      "https://clojars.org/repo"
                               :username (System/getenv "CLOJARS_USER")
                               :password (System/getenv "CLOJARS_PASS")}]])

(task-options!
  pom  {:project     'moxaj/macrowbar
        :version     "0.2.0"
        :description "Portable clojure macro utility functions"
        :url         "http://github.com/moxaj/macrowbar"
        :license     {"Eclipse Public License" "http://www.eclipse.org/legal/epl-v10.html"}}
  push {:ensure-clean  false
        :ensure-branch "master"
        :repo          "clojars"})

(deftask local-deploy
  "Installs the artifact into the local maven repository."
  []
  (comp (pom)
        (jar)
        (install)))

(deftask deploy
  "Installs the artifact into the local maven repository and pushes to clojars."
  []
  (comp (local-deploy)
        (push)))
