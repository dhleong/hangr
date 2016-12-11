(ns ^{:author "Daniel Leong"
      :doc "Notification"}
  hangr.util.notification
  (:require [hangr.util :refer [js->real-clj id->key]]))

(defonce notifier (js/require "node-notifier"))

(defn notify!
  "Raise a native notification. Takes an options map
  and, optionally, a callback that's called when the
  user provides a text reply to the notification (macOS only)."
  [& {:keys [title message icon reply? timeout
             on-reply on-click]
      :or {icon (str js/__dirname "app/img/logo-dark@4x.png")
           timeout 20}}] ;; so terminal-notifier doesn't linger if ignored
  {:pre [(string? title)
         (string? message)]}
  (-> notifier
      (.notify 
        #js {:title title
             :message message 
             :icon icon
             :reply reply?
             :timeout timeout}
        (fn [e resp & [reply]]
          (let [reply (js->real-clj reply)]
            (cond
              ;; did they reply inline?
              (and on-reply (:activationValue reply)) 
              (on-reply (:activationValue reply))
              ;; nope; did they tap to open the notif?
              (and (= "activate" resp)
                   on-click)
              (on-click)
              ;; else, ignored...
              :else 
              nil)))))) 

(defn conv-msg->title
  "Pick a 'Title' for the `msg` received in the
  `conv` appropriate for use in a notification"
  [conv msg]
  (let [sender-id (id->key (:sender_id msg))
        sender (->> conv
                    :members
                    (filter #(= (:id %)
                                sender-id))
                    first)]
    (if-let [sender-name (:name sender)]
      sender-name
      "New Hangouts Message"))) ;; fallback

(defn msg->notif
  "Extract text from a `msg` to be used in a notification"
  [msg]
  (->> msg
       :chat_message
       :message_content
       :segment
       (filter #(= "TEXT"
                   (:type %)))
       (map :text)
       (clojure.string/join " ")))