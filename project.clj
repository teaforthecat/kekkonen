(defproject metosin/kekkonen "0.2.0-SNAPSHOT"
  :description "A lightweight, remote (CQRS) API library for Clojure."
  :url "https://github.com/metosin/kekkonen"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "same as Clojure"}
  :dependencies [[prismatic/plumbing "0.5.2"]
                 [prismatic/schema "1.0.5"]
                 [frankiesardo/linked "1.2.6"]

                 ;; http-stuff, separate module?
                 [metosin/ring-swagger "0.22.4"]
                 [metosin/ring-swagger-ui "2.1.4-0"]
                 [metosin/ring-http-response "0.6.5"]
                 [ring-middleware-format "0.7.0"]

                 ;; client stuff, separate module?
                 [clj-http "2.1.0"]]
  :profiles {:dev {:plugins [[lein-midje "3.2"]]
                   :source-paths ["dev-src" "src"]
                   :dependencies [[org.clojure/clojure "1.8.0"]
                                  [criterium "0.4.4"]
                                  [http-kit "2.1.19"]
                                  ; required when working with Java 1.6
                                  [org.codehaus.jsr166-mirror/jsr166y "1.7.0"]
                                  [midje "1.8.3"]]}
             :perf {:jvm-opts ^:replace []}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}}
  :aliases {"all" ["with-profile" "dev:dev,1.7"]
            "perf" ["with-profile" "default,dev,perf"]
            "test-ancient" ["midje"]})
