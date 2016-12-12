(ns ^{:author "Daniel Leong"
      :doc "Notification"}
  hangr.util.notification
  (:require [hangr.util :refer [js->real-clj id->key]]))

(defonce notifier (js/require "node-notifier"))
(defonce bundle-id (.-bundleId (js/require (str js/__dirname "/package.json"))))

(defn notify!
  "Raise a native notification. Takes an options map
  and, optionally, a callback that's called when the
  user provides a text reply to the notification (macOS only)."
  [& {:keys [title message icon reply? timeout
             on-reply on-click]
      :or {timeout 20}}] ;; so terminal-notifier doesn't linger if ignored
  {:pre [(string? title)
         (string? message)]}
  (-> notifier
      (.notify 
        #js {:title title
             :message message 
             :icon icon
             :reply reply?
             :timeout timeout
             :sender bundle-id}
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
  (or
    (when-let [text-parts 
               (->> msg
                    :chat_message
                    :message_content
                    :segment
                    (filter #(= "TEXT"
                                (:type %)))
                    (map :text)
                    seq)]
      (clojure.string/join " " text-parts))
    ;
    (when-let [img-parts 
               (->> msg
                    :chat_message
                    :message_content
                    :attachment
                    seq)]
      "Sent you an image")
    ;
    (when-let [hangout-event (-> msg :hangout_event :event_type)]
      (println "HI")
      (case hangout-event
        "START_HANGOUT" "Call Started"
        "END_HANGOUT" "Call Ended"
        nil))
    ;
    (do
      ; NB: js/console gives us nicer inspection
      (.log js/console "Can't create text preview for" (clj->js msg))
      ; just return a blank string for safety
      "")))
