(ns target.log
  (:require [clojure.data.json :as json]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.local :as l]
            [environ.core :refer [env]]))

(def level-info "INFO")
(def level-warn "WARN")
(def level-error "ERROR")
(def log-org "core-prod")
(def log-space "tollans")
(def log-service "target")
(def log-version "1.0")
(def log-date-format (f/formatter "yyyy-MM-dd'T'HH:mm:ss'.'SSSZZ"))
(def default-base {
                           :type "unknown"
                           :org "unknown"
                           :space "unknown"
                           :service "unknown"
                           :version "unknown"
                           :instance "unknown"})
(defonce log-state (atom {:msg-base default-base}))

(defn init!
  ([{:keys [instance_index] :or {instance_index "unknown"}}]
   (swap! log-state assoc :msg-base
          {:type "service"
           :org log-org
           :space log-space
           :service log-service
           :version log-version
           :instance instance_index}))
  ([]
   (init! (:vcap-application env))))

(defn base 
  ([] (base {}))
  ([{:keys [level tags] :or {level "INFO" tags []}}]
  (assoc (:msg-base @log-state)
         :time (f/unparse log-date-format (l/local-now))
         :level (str level)
         :tags (map str tags))))

(defn ->json [l]
  (str (json/write-str l :escape-slash false :escape-unicode false)))

(defn ->stdout [l]
  (-> l
      (->json)
      (println)))

(defn metric 
  "Name n identifies the metric.
  The type t defines its data type,
  valid are number, string."
  ([t n v]
  (->stdout
     (assoc (base)
           :metric {
                    :type (clojure.string/lower-case t)
                    :metric (str n) 
                    :value v})))
  ([n v]
   (metric "string" n v)))

(defn msg 
  ([m {:keys [request-id vcap-request-id hop] :or {request-id "unknown" vcap-request-id "unknown" hop "unknown"} :as c}]
  (->stdout
    (assoc (base c)
           :log {
                 :logger "default"
                 :hop hop
                 :requestId (str request-id)
                 :vcapRequestId (str vcap-request-id)
                 :message (str m)})))
  ([m] (msg m {})))

(defn info
  ([m c] (msg m (assoc c :level level-info)))
  ([m] (info m {})))

(defn warn 
  ([m c] (msg m (assoc c :level level-warn)))
  ([m] (warn m {})))

(defn error 
  ([m c] (msg m (assoc c :level level-error)))
  ([m] (error m {})))
