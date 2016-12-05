(ns ^{:author "Daniel Leong"
      :doc "Effects"}
  hangr.fx
  (:require [re-frame.core :refer [reg-fx]]
            [goog.dom :refer [getElement]]
            [goog.fx.dom :refer [Scroll]]))

(defonce electron (js/require "electron"))
(defonce ipc-renderer (.-ipcRenderer electron))
(defonce shell (.-shell electron))

(reg-fx
  :ipc
  (fn [[event arg]]
    (.send ipc-renderer (name event) arg)))

(reg-fx
  :open-external
  (fn [url]
    (.openExternal shell url)))

(reg-fx
  :scroll-to-bottom
  (fn [do-scroll?]
    (when do-scroll?
      (println "do-scroll=" do-scroll?)
      (.play
        (Scroll. 
          js/document.body
          #js [0, 0]
          #js [0, 99999] ;; cheat?
          20)))))
