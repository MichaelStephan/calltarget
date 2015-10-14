(defproject callanalyzer "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.taoensso/timbre "4.1.1"]
                 [slingshot "0.12.2"]
                 [compojure "1.3.4"]
                 [http-kit "2.1.18"]
                 [ring "1.1.3"]
                 [environ "0.5.0"]
                 [overtone/at-at "1.2.0"]
                 [org.clojure/data.json "0.2.6"]
                 [clj-time "0.11.0"]
                 [com.taoensso/timbre "4.1.4"]]
  :global-vars {*warn-on-reflection* true
                *assert* true})
