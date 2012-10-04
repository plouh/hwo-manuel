(defproject cloxy "1.0.0-SNAPSHOT"
  :description "Cloxy"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [org.clojure/algo.monads "0.1.0"]
                 [aleph "0.3.0-beta4"]
                 [overtone/at-at "1.0.0"]
                 [ring/ring-core "1.1.0"]
                 [ring/ring-jetty-adapter "1.1.0"]
                 [org.slf4j/slf4j-api "1.6.4"]
                 [ch.qos.logback/logback-classic "1.0.3"]
                 ]
  :dev-dependencies [[swank-clojure "1.4.2"]
                     [lein-run "1.0.1-SNAPSHOT"]]
  :run-aliases {:server [cloxy]}
  :aot [cloxy.core]
  :main cloxy.server)
