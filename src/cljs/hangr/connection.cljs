(ns ^{:author "Daniel Leong"
      :doc "Hangouts connection handling"}
  hangr.connection
  (:require [re-frame.core :refer [dispatch]]
            [hangr.util :refer [js->real-clj id->key]]))

(defonce electron (js/require "electron"))
(defonce ipc (.-ipcRenderer electron))

(defn entity->clj
  [js-entity]
  (-> js-entity
      js->real-clj
      (as-> entity
        (let [props (:properties entity)]
          (assoc props
                 :name (:display_name props)
                 :id (id->key (:id entity)))))))

(defn event->clj
  "Converts an event object (which may be a JS type OR already
  a Clojure map) into a format appropriate for use in hangr"
  [js-event]
  (let [js-event (if (identical? (type js-event) js/Object)
                   (js->real-clj js-event)
                   js-event)]
    (assoc js-event 
           :id (:event_id js-event)
           :sender (id->key (:sender_id js-event)))))

(defn conv->clj
  "Clean up a js conv object to something sane"
  [js-conv]
  (-> js-conv
      js->real-clj
      (as-> c 
        (assoc c 
               ;; let the right id in
               :id 
               (or (-> c :conversation_id :id)
                   (-> c :conversation :conversation_id :id))
               ;; create a nice list of members (participants)
               :members
               (->> c
                    :conversation
                    :current_participant
                    (map 
                      (fn [raw-id]
                        {:id (id->key raw-id)
                         ;; extract a fallback name (if possible)
                         ;;  by finding the first (only) participant
                         ;;  data with a matching id
                         :name 
                         (->> c
                              :conversation
                              :participant_data
                              (filter #(= raw-id
                                          (:id %)))
                              first
                              :fallback_name)})))
               ;; clean up `:event` and put it in :events
               :events
               (->> (:event c)
                    (remove #(let [ev-id (:event_id %)]
                               (or
                                 (not ev-id)
                                 (clojure.string/starts-with?
                                   ev-id
                                   "observed_"))))
                    (map event->clj))
               ;; clean up some self info
               :self
               (let [self-conv-state (-> c :conversation :self_conversation_state)
                     self-read-state (-> self-conv-state :self_read_state)]
                 {:id (-> self-read-state :participant_id id->key)
                  :latest-read-timestamp (:latest_read_timestamp self-read-state)})))
      ;; remove some unnecessary keys
      (dissoc :event)))

(defn self->clj
  "Clean up our 'self' info"
  [js-self]
  (-> js-self
      js->real-clj
      (as-> s
        (assoc s
               :id (-> s :self_entity :id id->key)))))

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
