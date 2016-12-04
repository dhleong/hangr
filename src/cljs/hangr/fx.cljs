(ns ^{:author "Daniel Leong"
      :doc "Effects"}
  hangr.fx
  (:require [re-frame.core :refer [reg-fx]]))

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
