(ns ^{:author "Daniel Leong"
      :doc "Hangouts connection handling"}
  hangr.connection
  (:require [re-frame.core :refer [dispatch]]
            [hangr.util :refer [js->real-clj id->key]]
            [hangr.util.parse :refer [entity->clj event->clj 
                                      conv->clj self->clj]]))

(defonce electron (js/require "electron"))
(defonce ipc (.-ipcRenderer electron))

(def ipc-listeners
  {:connected
   (fn on-connected []
     (println "Connected!")
     (dispatch [:connected]))

   :got-entities
   (fn on-entities
     [_ entities]
     (.log js/console "Got entities" entities)
     (doseq [person (map entity->clj entities)]
       (dispatch [:update-person person])))

   :recent-conversations
   (fn [_ convs]
     (.log js/console "Got conversations" convs)
     (doseq [conv (map conv->clj convs)]
       (dispatch [:update-conv conv])))
   
   :self-info
   (fn [_ info]
     (.log js/console "Got self info" info)
     (dispatch [:set-self (self->clj info)]))

   :send
   (fn [_ conv-id sending-msg-event]
     (.log js/console "Sending..." sending-msg-event)
     (dispatch [:sending-msg conv-id (js->real-clj sending-msg-event)]))
   
   :sent
   (fn [_ sent-msg-event]
     (.log js/console "Sent" sent-msg-event)
     (let [ev (event->clj sent-msg-event)]
       (dispatch [:update-sent (-> ev :conversation_id :id) ev])))
   
   :received
   (fn [_ received-msg-event]
     (.log js/console "Received" received-msg-event)
     (let [ev (event->clj received-msg-event)]
       (dispatch [:receive-msg (-> ev :conversation_id :id) ev])))})

;; NB: since this isn't really an SPA, we handle
;;  the hangouts connection in the parent process
;;  and communicate over IPC

(defn init!
  []
  (.send ipc "request-status")
  (doseq [[event-id handler] ipc-listeners]
    (let [event-id (name event-id)] 
      (.removeAllListeners ipc event-id)
      (.on ipc event-id handler))))
