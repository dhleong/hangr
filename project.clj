(defproject hangr "0.1.0-alpha1"
  :description "Hangouts, the way it was meant to be"
  :url "http://github.com/dhleong/hangr"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/cljs"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.293"]
                 [reagent "0.6.0"]
                 [re-frame "0.9.0"]
                 [secretary "1.2.3"]]

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-less "1.7.5"]]

  :hooks [leiningen.cljsbuild]

  :min-lein-version "2.5.3"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "app/js/p/app.js"
                                        :output-dir    "app/js/p/out"
                                        :asset-path    "js/p/out"
                                        :optimizations :none
                                        :pretty-print  true
                                        :cache-analysis true}}}
              
              :test-commands
              {"frontend" ["lein" "doo" "node" "once"]}}

  :clean-targets ^{:protect false} [:target-path "out" "app/js/p"]

  :figwheel {:css-dirs ["app/css"]}

  :test-paths ["test/cljs"]

  :less {:source-paths ["app/css"]
         :target-path  "app/css"}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles {:dev {:cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {:source-map true
                                                         :main       "hangr.dev"
                                                         :verbose true}
                                              :figwheel {:on-jsload "hangr.core/mount-root"}}
                                        :node-test {:source-paths ["env/dev/cljs" "src" "test"]
                                                    :compiler {:main "hangr.node-runner"
                                                               :output-to "app/js/p/testable.js"
                                                               :optimizations :none
                                                               :target :nodejs}}}}
                   :source-paths ["env/dev/cljs"]

                   :doo {:build "node-test"}

                   :dependencies [[binaryage/devtools "0.8.3"]
                                  [re-frisk "0.3.2"]
                                  [figwheel-sidecar "0.5.8"]
                                  [com.cemerick/piggieback "0.2.1"]]

                   :plugins [[lein-ancient "0.6.10"]
                             [lein-kibit "0.1.3"]
                             [lein-cljfmt "0.5.6"]
                             [lein-figwheel "0.5.8"]
                             [lein-doo "0.1.7"]]}

             :production {:cljsbuild {:builds {:app {:compiler {:optimizations :advanced
                                                                :main          "hangr.prod"
                                                                :parallel-build true
                                                                :cache-analysis false
                                                                :closure-defines {"goog.DEBUG" false}
                                                                :externs ["externs/misc.js"]
                                                                :pretty-print false}
                                                     :source-paths ["env/prod/cljs"]}}}}}
  )
