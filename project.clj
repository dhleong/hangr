(def npm-deps {:url-regex "4.1.1"})

(defproject hangr "0.10.0-beta"
  :description "Hangouts, the way it was meant to be"
  :url "http://github.com/dhleong/hangr"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/cljs"]

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.439"]
                 [reagent "0.8.1"]
                 [re-frame "0.10.6"]
                 [hickory "0.7.1"]
                 [clj-commons/secretary "1.2.4"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-less "1.7.5"]]

  :hooks [leiningen.cljsbuild]

  :min-lein-version "2.5.3"

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "app/js/p/app.js"
                                        :output-dir    "app/js/p/out"
                                        :asset-path    "js/p/out"
                                        :optimizations :none
                                        :pretty-print  true
                                        :cache-analysis true
                                        :preloads      [devtools.preload]

                                        :npm-deps      ~npm-deps
                                        :install-deps  true

                                        :external-config {:devtools/config {:features-to-install :all}}
                                        }}}

              :test-commands
              {"frontend" ["lein" "do"
                           "doo" "node" "node-test" "once"
                           ","
                           "doo" "chrome" "chrome-test" "once"]}}

  :clean-targets ^{:protect false} [:target-path "out" "app/js/p"]

  :figwheel {:css-dirs ["resources/public/css"]
             :builds-to-start ["app"]
             :nrepl-port 7002
             :server-ip "0.0.0.0"
             :server-port 3451
             :nrepl-middleware
             [cemerick.piggieback/wrap-cljs-repl cider.nrepl/cider-middleware]}

  :test-paths ["test/cljs"]

  :less {:source-paths ["app/css"]
         :target-path  "app/css"}

  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles {:dev {:cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {:source-map true
                                                         :main       hangr.core
                                                         :verbose true}
                                              :figwheel {:on-jsload "hangr.core/mount-root"}}
                                        :chrome-test {:source-paths ["env/dev/cljs" "src" "test"]
                                                      :compiler {:main "hangr.chrome-runner"
                                                                 :output-dir "app/js/p/out-chrome"
                                                                 :output-to "app/js/p/testable-chrome.js"
                                                                 :npm-deps ~npm-deps
                                                                 :optimizations :none}}
                                        :phantom-test {:source-paths ["env/dev/cljs" "src" "test"]
                                                       :compiler {:main "hangr.runner"
                                                                  :output-dir "app/js/p/out-phantom"
                                                                  :output-to "app/js/p/testable-phantom.js"
                                                                  :npm-deps ~npm-deps
                                                                  :optimizations :none}}
                                        :node-test {:source-paths ["env/dev/cljs" "src" "test"]
                                                    :compiler {:main "hangr.node-runner"
                                                               :output-dir "app/js/p/out-node"
                                                               :output-to "app/js/p/testable-node.js"
                                                               :optimizations :none
                                                               :npm-deps ~npm-deps
                                                               :target :nodejs}}}}

                   :doo {:build "node-test"
                         :paths {:karma "./node_modules/karma/bin/karma"}}

                   :dependencies [[binaryage/devtools "0.9.10"]
                                  [re-frisk "0.5.4"]
                                  [figwheel-sidecar "0.5.17"]
                                  [com.cemerick/piggieback "0.2.2"]
                                  [doo "0.1.10"]
                                  [day8.re-frame/test "0.1.5"]]

                   :plugins [[lein-ancient "0.6.15"]
                             [lein-kibit "0.1.6"]
                             [lein-cljfmt "0.6.1"]
                             [lein-figwheel "0.5.17"]
                             [lein-doo "0.1.10"]]}

             :production {:cljsbuild {:builds {:app {:compiler {:optimizations :advanced
                                                                :main          "hangr.core"
                                                                :parallel-build true
                                                                :cache-analysis false
                                                                :closure-defines {"goog.DEBUG" false}
                                                                :externs ["externs/misc.js"
                                                                          "externs/package.js"
                                                                          "externs/request.js"
                                                                          "externs/semver.js"]
                                                                :pretty-print false}
                                                     :source-paths ["env/prod/cljs"]}}}}})
