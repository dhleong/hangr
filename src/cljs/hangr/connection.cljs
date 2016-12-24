(ns ^{:author "Daniel Leong"
      :doc "Hangouts connection handling"}
  hangr.connection
  (:require [re-frame.core :refer [dispatch]]
            [clojure.string :as string]
            [hangr.util :refer [js->real-clj id->key]]
            [hangr.util.parse :refer [entity->clj event->clj conv->clj self->clj
                                      watermark->clj]]))

(defonce electron (js/require "electron"))
(defonce ipc (.-ipcRenderer electron))

(def ipc-listeners
  {:connected
   (fn on-connected []
     (println "Connected!")
     (dispatch [:connected]))

   :focus
   (fn on-focus
     [js-focus-event]
     (let [focus-event (js->real-clj js-focus-event)]
       (.log js/console "Got focus-event" focus-event)
       (dispatch [:update-focus
                  (-> focus-event :conversation_id :id)
                  (-> focus-event :user_id id->key)
                  (= "FOCUSED" (:status focus-event))
                  (:device focus-event)])))

   :got-entities
   (fn on-entities
     [entities]
     (.log js/console "Got entities" entities)
     (doseq [person (map entity->clj entities)]
       (dispatch [:update-person person])))

   :received
   (fn [received-msg-event]
     (.log js/console "Received" received-msg-event)
     (let [ev (event->clj received-msg-event)]
       (dispatch [:receive-msg (-> ev :conversation_id :id) ev])))
   
   :recent-conversations
   (fn [convs]
     (.log js/console "Got conversations" convs)
     (doseq [conv (map conv->clj convs)]
       (dispatch [:update-conv conv])))
   
   :self-info
   (fn [info]
     (.log js/console "Got self info" info)
     (dispatch [:set-self (self->clj info)]))

   :send
   (fn [conv-id sending-msg-event]
     (.log js/console "Sending..." sending-msg-event)
     (dispatch [:sending-msg conv-id (js->real-clj sending-msg-event)]))
   
   :sent
   (fn [sent-msg-event]
     (.log js/console "Sent" sent-msg-event)
     (let [ev (event->clj sent-msg-event)]
       (dispatch [:update-sent (-> ev :conversation_id :id) ev])))

   :typing
   (fn [typing-event]
     (let [ev (js->real-clj typing-event)
           conv-id (-> ev :conversation_id :id)
           user-id (id->key (:user_id ev))
           status (-> ev :status string/lower-case keyword)]
       (.log js/console "typing" [conv-id user-id status])
       (dispatch [:update-typing
                  conv-id
                  user-id
                  status])))
   
   :watermark
   (fn [watermark-event]
     (.log js/console "Watermark" watermark-event)
     (let [ev (watermark->clj watermark-event)]
       (dispatch [:update-watermark 
                  (-> ev :conversation_id :id) 
                  (:sender ev)
                  (:latest_read_timestamp ev)])))})

;; NB: since this isn't really an SPA, we handle
;;  the hangouts connection in the parent process
;;  and communicate over IPC

(defn init!
  []
  (.send ipc "request-status")
  (doseq [[event-id handler] ipc-listeners]
    (let [event-id (name event-id)] 
      (.removeAllListeners ipc event-id)
      (.on ipc 
           event-id 
           (fn event-handler-wrapper
             [e & args]
             (apply handler args))))))
