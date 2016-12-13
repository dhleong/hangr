(ns ^{:author "Daniel Leong"
      :doc "Effects"}
  hangr.fx
  (:require [re-frame.core :refer [reg-fx dispatch]]
            [goog.dom :refer [getElement]]
            [goog.fx.dom :refer [Scroll]]
            [hangr.util :refer [key->id]]
            [hangr.util.conversation :refer [unread?]]
            [hangr.util.notification :refer [notify! conv-msg->title msg->notif]]))

(defonce electron (js/require "electron"))
(defonce ipc-renderer (.-ipcRenderer electron))
(defonce shell (.-shell electron))

(defonce get-entities-queue (atom []))
(defonce get-entities-timer (atom nil))
(def get-entities-delay 10)

(defonce set-unread-timer (atom nil))
(def set-unread-delay 10)

(defn- ipc!
  [event & args]
  (let [ipc-args
        (into-array
          (cons (name event) 
                (map clj->js args)))]
    (.log js/console "IPC" ipc-args)
    (.apply (.-send ipc-renderer)
            ipc-renderer
            ipc-args)))

(defn- run-get-entities-queue
  []
  (reset! get-entities-timer nil)
  (when-let [queue (seq (distinct @get-entities-queue))]
    (reset! get-entities-queue [])
    (ipc! :get-entities queue)))

(reg-fx
  :ipc
  (fn [[event & args]]
    (when event
      (apply ipc! event args))))

(reg-fx
  :check-unread
  (fn [convs-map]
    (when convs-map
      (let [any-unread (not (nil? 
                              (some unread? 
                                    (vals convs-map))))]
        (when-let [timer @set-unread-timer]
          (js/clearTimeout timer))
        (reset! set-unread-timer
                (js/setTimeout
                  #(ipc! :set-unread! any-unread)
                  set-unread-delay))))))

(reg-fx
  :get-entities
  (fn [ids]
    (when (seq ids)
      ;; queue up the ids
      (swap! get-entities-queue concat (map (comp :chat_id key->id) ids))
      ;; if we don't already have a running timer, start one
      (when (nil? @get-entities-timer)
        (reset! get-entities-timer 
                (js/setTimeout run-get-entities-queue 
                               get-entities-delay))))))

(reg-fx
  :open-external
  (fn [url]
    (.openExternal shell url)))

(reg-fx
  :notify-chat!
  (fn [[conv msg]]
    (when msg
      (notify!
        :title (conv-msg->title conv msg)
        :message (msg->notif msg)
        :icon nil ;; TODO sender's avatar
        :reply? "Reply"
        :on-reply
        (fn [reply]
          (dispatch [:send-html (:id conv) reply]))
        :on-click
        (fn []
          (dispatch [:select-conv (:id conv)]))))))

(reg-fx
  :scroll-to-bottom
  (fn [do-scroll?]
    (when do-scroll?
      (.play
        (Scroll. 
          js/document.body
          #js [0, (.-scrollTop js/document.body)]
          #js [0, 99999] ;; cheat?
          20)))))
