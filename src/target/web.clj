(ns target.web
  (:require [target.log :as log]
            [org.httpkit.server :as server]
            [org.httpkit.client :as client]
            [environ.core :refer [env]]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [taoensso.timbre.appenders.core :as appenders]
            [ring.middleware.keyword-params :as keyword-params]
            [ring.middleware.params :as params]
            [clojure.core.async :as a :refer [take! >! >!! <!! <! go chan timeout]]
            [taoensso.timbre :as timbre :refer (spy)]
            [clojure.data.json :as json])
  (:use [clojure.inspector :only [inspect inspect-tree]]
        [overtone.at-at])
  (:gen-class))

(defonce at-pool (mk-pool))
(defonce server-state (atom nil))
(defonce test-state (atom {}))
(def request-id-header-name "hybris-request-id")
(def vcap-requestid-header-name "x-vcap-request-id")

(def http-options {:timeout (reduce * [1000 60 5]) :user-agent "target-app"})

(defn cfapp? [] (:vcap-application env))

(defn get-port [] (try
                    (read-string (:port env))
                    (catch Exception e
                      (log/warn "No PORT environment variable set, using default")
                      8080)))

(defn get-direct-endpoint []
  (if-let [url (if (cfapp?) (str "https://" (-> env :vcap-application (json/read-str) (get "uris") first)))]
    url
    (str "http://localhost:" (get-port))))

(defn handle-async! [handler req]
  (server/with-channel req channel
    (take! (handler req) #(server/send! channel %))))

(defn uuid [] (str (java.util.UUID/randomUUID)))

(defn handle-test! [{:keys [headers] :as req} {:keys [url work-fn hop more-hops?]}]
  (let [hybris-request-id (get headers request-id-header-name (uuid))
        vcap-request-id (get headers vcap-requestid-header-name "")]
    
    (log/info (str "calling " url) {:hop hop :request-id hybris-request-id :vcap-request-id vcap-request-id})
    (let [r (chan)]
        (if more-hops? 
          (try
            (client/get url (assoc-in http-options [:headers request-id-header-name] hybris-request-id)
                        (fn [{:keys [status headers body error]}]
                          (if error
                            (>!! r {:status 500 :body error})
                            (go
                              (<! (work-fn))
                              (>! r {:status status :headers {request-id-header-name hybris-request-id}})))))
            (catch Exception _ (>!! r {:status 500})))
          (go 
            (<! (work-fn))
            (>! r {:status 200 :headers {request-id-header-name hybris-request-id}})))
      r)))

(defn rand-work-fn [v] {:pre [(number? v)]} #(timeout (rand-int v)))

(defn fixed-work-fn [v] {:pre [(number? v)]} #(timeout v))

(defn test-direct-random-endpoint [i j v] (str (get-direct-endpoint) "/test/direct/hop/" i "/" j "/random/" v))

(defn test-direct-fixed-endpoint [i j v] (str (get-direct-endpoint) "/test/direct/hop/" i "/" j "/fixed/" v))

(defroutes app 
  (context "/test/direct/hop/:i/:j" [i j]
           (GET "/random/:v" [v] (let [i (read-string i) j (read-string j) v (read-string v)]
                                   (partial handle-async! #(handle-test! % {:url (test-direct-random-endpoint (inc i) j v) 
                                                                            :hop i :more-hops? (< i j)
                                                                            :work-fn (rand-work-fn v)}))))
           (GET "/fixed/:v" [v] (let [i (read-string i) j (read-string j) v (read-string v)]
                                  (partial handle-async! #(handle-test! % {:url (test-direct-fixed-endpoint (inc i) j v)
                                                                           :hop i :more-hops? (< i j)
                                                                           :work-fn (fixed-work-fn v)}))))
           (route/not-found {:status 404})))

(defn start-server! []
  (log/info (get-direct-endpoint))
  
  (if (cfapp?) 
    (log/info (str "Running as cloud foundry application " (:vcap-application env)))
    (log/info "Running as standalone application"))
  (reset! server-state (server/run-server #'app {:port (get-port)})))

(defn stop-server! []
  (when-not (nil? @server-state)
    (@server-state :timeout 100)
    (reset! server-state nil)))

(defn -main [& args]
  (log/init!)
  (start-server!))
