(ns ^{:author "Daniel Leong"
      :doc "Notification"}
  hangr.util.notification
  (:require [hangr.util :refer [js->real-clj id->key safe-require
                                read-package-json]]))

(defonce electron (safe-require "electron"))
(defonce ipc-renderer (.-ipcRenderer electron))

(defonce ^:private notification-handlers (atom {}))

(defn notify!
  "Raise a native notification. Takes an options map
  and, optionally, a callback that's called when the
  user provides a text reply to the notification (macOS only)."
  [& {:keys [title message icon reply?
             close-label actions
             on-reply on-click]
      :or {timeout 20}}] ;; so terminal-notifier doesn't linger if ignored
  {:pre [(string? title)
         (string? message)]}
  (let [params
        (->> {:title title
              :body message
              :icon icon
              :id (str (random-uuid) (.getTime (js/Date.)))
              :hasReply (when reply?
                          true)
              :replyPlaceholder (when reply?
                                  reply?)
              :closeButtonLabel close-label
              :actions actions}
             (filter second)
             (into {}))]
    ;; FIXME there's a lot of unnecessary indirection going on here
    ;;  since we weren't previously using IPC for notifications....
    (js/console.log "NOTIFY!" params)
    (when (or on-reply on-click)
      (swap! notification-handlers
             assoc
             (:id params)
             {:on-click on-click
              :on-reply on-reply}))
    (.send ipc-renderer "notify!" (clj->js params))))

(defn dispatch-action
  "Dispatch a response action to a notification"
  [action]
  (println "Dispatch " action "...")
  (when-let [handlers (get @notification-handlers (:id action))]
    (swap! notification-handlers dissoc (:id action))
    (let [[handler arg] (case (:type action)
                          "reply" [(:on-reply handlers) (:reply action)]
                          "click" [(:on-click handlers)]
                          "action" [(:on-click handlers)]
                          :else nil)] ;; eg: "close"
      (when handler
        (println "Dispatch for" (:id action) handler arg)
        (handler arg)))))

(defn conv-msg->title
  "Pick a 'Title' for the `msg` received in the
  `conv` appropriate for use in a notification"
  [conv msg & {:keys [fallback]
               :or {fallback "New Hangouts Message"}}]
  (let [sender-id (id->key (:sender_id msg))
        sender (->> conv
                    :members
                    sender-id)]
    (if-let [sender-name (:name sender)]
      sender-name
      fallback)))

(defn msg->notif
  "Extract text from a `msg` to be used in a notification"
  [msg]
  (or
    (when-let [text-parts 
               (->> msg
                    :chat_message
                    :message_content
                    :segment
                    (filter #(let [seg-type (:type %)]
                               (or (= "TEXT" seg-type)
                                   (= "LINK" seg-type))))
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
      (case hangout-event
        "START_HANGOUT" "Call Started"
        "END_HANGOUT" "Call Ended"
        nil))
    ;
    (do
      ; NB: js/console gives us nicer inspection
      (js/console.log "Can't create text preview for" (clj->js msg))
      ; just return a blank string for safety
      "")))
