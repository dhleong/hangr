(ns ^{:author "Daniel Leong"
      :doc "Effects"}
  hangr.fx
  (:require [re-frame.core :refer [reg-fx dispatch]]
            [goog.dom :refer [getElement]]
            [goog.fx.dom :refer [Scroll]]
            [hangr.util.notification :refer [notify! conv-msg->title msg->notif]]))

(defonce electron (js/require "electron"))
(defonce ipc-renderer (.-ipcRenderer electron))
(defonce shell (.-shell electron))

(reg-fx
  :ipc
  (fn [[event & args]]
    (.log js/console "IPC" (into-array
                             (cons (name event) 
                                   (map clj->js args))))
    (.apply (.-send ipc-renderer)
           ipc-renderer
           (into-array
             (cons (name event) 
                   (map clj->js args))))))

(reg-fx
  :open-external
  (fn [url]
    (.openExternal shell url)))

(reg-fx
  :notify-chat!
  (fn [[conv msg]]
    (when msg
      (notify!
        {:title (conv-msg->title conv msg)
         :message (msg->notif msg)
         :reply? "Reply"}
        (fn [reply]
          (dispatch [:send-html (:id conv) reply]))))))

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
