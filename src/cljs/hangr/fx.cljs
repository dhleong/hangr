(ns ^{:author "Daniel Leong"
      :doc "Effects"}
  hangr.fx
  (:require [re-frame.core :refer [reg-fx dispatch]]
            [goog.dom :refer [getElement]]
            [goog.fx.dom :refer [Scroll]]
            [hangr.util :refer [key->id safe-require]]
            [hangr.util.conversation :refer [unread?]]
            [hangr.util.notification :refer [notify! conv-msg->title msg->notif]]
            [hangr.util.updates :refer [check-update!]]))

; nil in phantom tests
(defonce electron (safe-require "electron"))
(defonce ipc-renderer (.-ipcRenderer electron))
(defonce shell (.-shell electron))

;; -- Nullable dispatch -------------------------------------------------------
;;

(reg-fx
  :dispatch?
  (fn [args]
    (when args
      (dispatch args))))

(reg-fx
  :dispatch-n?
  (fn [args]
    (doseq [event args]
      (when event
        (dispatch event)))))

;; -- IPC messaging -----------------------------------------------------------
;;

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

(reg-fx
  :ipc
  (fn [[event & args]]
    (when event
      (apply ipc! event args))))


;; -- Check Unread ------------------------------------------------------------
;;

(defonce set-unread-timer (atom nil))
(def set-unread-delay 10)

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

;; -- Check for app updates ---------------------------------------------------
;;

; runs once every 24 hours
(defonce check-update-timer (atom nil))
(def check-update-delay (* 24 3600 1000))

(reg-fx
  :reset-update-checker!
  (fn [running?]
    (when-not running?
      (when-let [timer @check-update-timer]
        (println "Clear old update checker")
        (js/clearInterval timer)
        (reset! check-update-timer nil)))
    (when running?
      (println "Start update check")
      (reset! check-update-timer
              (js/setInterval
                check-update!
                check-update-delay)))))

;; -- Close the window --------------------------------------------------------
;;

(reg-fx
  :close-window!
  (fn [do-close?]
    (when do-close?
      (js/window.close))))

;; -- Get Entities ------------------------------------------------------------
;;

(defonce get-entities-queue (atom []))
(defonce get-entities-timer (atom nil))
(def get-entities-delay 10)

(defn- run-get-entities-queue
  []
  (reset! get-entities-timer nil)
  (when-let [queue (seq (distinct @get-entities-queue))]
    (reset! get-entities-queue [])
    (ipc! :get-entities queue)))

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

;; -- Open External -----------------------------------------------------------
;;

(reg-fx
  :open-external
  (fn [url]
    (.openExternal shell url)))

;; -- General Notification ----------------------------------------------------
;;

(reg-fx
  :notify!
  (fn [opts]
    (when opts
      (apply notify! (flatten (seq opts))))))

;; -- Chat Notification -------------------------------------------------------
;;

(reg-fx
  :notify-chat!
  (fn [[conv msg]]
    (let [hangout-type (-> msg :hangout_event :event_type)]
      (when msg
        (cond
          ;
          ; incoming hangout
          (= "START_HANGOUT" hangout-type)
          (notify!
            :title (conv-msg->title conv msg "Incoming Hangout")
            :message "Incoming Hangouts Call"
            :icon nil ;; TODO sender's avatar
            :wait? true
            :on-click
            (fn []
              (dispatch [:open-hangout (:id conv)])))
          ;
          ; hangout ended (missed?)
          (= "END_HANGOUT" hangout-type)
          nil ; TODO notify if missed?
          ;
          ; else, normal chat message:
          :else
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
              (dispatch [:select-conv (:id conv)]))))))))

;; -- Scroll Page to bottom ---------------------------------------------------
;;

(reg-fx
  :scroll-to-bottom
  (fn [do-scroll?]
    (when do-scroll?
      (let [scroller 
            (or
              (aget (.getElementsByClassName js/document "scroll-host") 0)
              (.getElementById js/document "app-container"))]
        (.play
          (Scroll. 
            scroller
            #js [0, (.-scrollTop scroller)]
            #js [0, 99999] ;; cheat?
            20))))))

;; -- Typing notifications ----------------------------------------------------
;;

(defonce typing!-timer (atom nil))
(def typing!-delay 3000)

(reg-fx
  :typing!
  (fn [[conv-id stop?]]
    (if stop?
      (do
        (when-let [timer @typing!-timer]
          (js/clearTimeout timer)
          (reset! typing!-timer nil))
        (ipc! :set-typing! conv-id :stopped))
      (do
        (if-let [timer @typing!-timer]
          ; if there's a timer, clear it
          (js/clearTimeout timer)
          ; ... otherwise, send :typing
          (ipc! :set-typing! conv-id :typing))
        ; in any case, start a new timer
        (reset! typing!-timer
                (js/setTimeout 
                  (fn []
                    (reset! typing!-timer nil)
                    (ipc! :set-typing! conv-id :paused))
                  typing!-delay))))))

