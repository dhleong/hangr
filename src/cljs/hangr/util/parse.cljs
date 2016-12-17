(ns ^{:author "Daniel Leong"
      :doc "Utils for parsing data retrieved over the connection"}
  hangr.util.parse
  (:require [hangr.util :refer [js->real-clj id->key]]))

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
               ;; clean up read states
               :read-states
               (->> c
                    :conversation
                    :read_state
                    (map
                      (fn [state]
                        {(-> state :participant_id id->key)
                         {:latest-read-timestamp (:last_read_timestamp state)}}))
                    (into {}))
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

(defn watermark->clj
  [js-watermark]
  (-> js-watermark
      js->real-clj
      (as-> w
        (assoc w
               :sender (-> w :participant_id id->key)))))
