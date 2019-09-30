(defproject ont-app/prototypes "0.1.0-SNAPSHOT"
  :description "A lightweight knowledge-rep ontology with supporting code. Allows for default logic."
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.9.1"

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/core.async  "0.4.500"]
                 [com.taoensso/timbre "4.10.0"]
                 [ont-app/igraph "0.1.4-SNAPSHOT"]
                 [ont-app/vocabulary "0.1.0-SNAPSHOT"]
                 [ont-app/igraph-vocabulary "0.1.0-SNAPSHOT"]

                 ]

  :plugins [[lein-figwheel "0.5.19"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src/cljc" "src/clj" "src/cljs"]
  :resource-paths ["resources" "target/cljsbuild"]
  :test-paths ["test/clj"]
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljc" "src/clj" "src/cljs" "test/cljs"]

                ;; The presence of a :figwheel configuration here
                ;; will cause figwheel to inject the figwheel client
                ;; into your build
                :figwheel {:on-jsload "prototypes.core/on-js-reload"
                           ;; :open-urls will pop open your application
                           ;; in the default browser once Figwheel has
                           ;; started and compiled your application.
                           ;; Comment this out once it no longer serves you.
                           ;; :open-urls ["http://localhost:3451/index.html"]
                           }

                :compiler {:main ont-app.prototypes.core ;;prototypes.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/prototypes.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           ;; To console.log CLJS data-structures make sure you enable devtools in Chrome
                           ;; https://github.com/binaryage/cljs-devtools
                           :preloads [figwheel.preload
                                      devtools.preload]}}
               ;; This next build is a compressed minified build for
               ;; production. You can build this with:
               ;; lein cljsbuild once min
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/compiled/prototypes.js"
                           :main prototypes.core
                           :optimizations :advanced
                           :pretty-print false}}]}

  :figwheel {;; :http-server-root "public" ;; default and assumes "resources"
             ;;:server-port 3451 ;; default
             :server-port ~(if-let [p (System/getenv "FIGWHEEL_SERVER_PORT")]
                             (read-string p)
                             3451)
             ;; :server-ip "127.0.0.1"

             :css-dirs ["resources/public/css"] ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888 
             :nrepl-port ~(if-let [p (System/getenv "NREPL_PORT")]
                             (read-string p)
                             7888)
             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this

             ;; doesn't work for you just run your own server :) (see lein-ring)

             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you are using emacsclient you can just use
             ;; :open-file-command "emacsclient"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log"

             ;; to pipe all the output to the repl
             ;; :server-logfile false
             }

  :profiles {:dev {:dependencies [[binaryage/devtools "0.9.10"]
                                  [figwheel-sidecar "0.5.19"]]
                   ;; need to add dev source path here to get user.clj loaded
                   :source-paths ["src/cljc" "src/clj" "src/cljs" "dev"]
                   ;; need to add the compliled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                                     :target-path]}})
