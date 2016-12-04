(ns ^{:author "Daniel Leong"
      :doc "Effects"}
  hangr.fx
  (:require [re-frame.core :refer [reg-fx]]))

(defonce ipc-renderer (.-ipcRenderer (js/require "electron")))

(reg-fx
  :ipc
  (fn [[event arg]]
    (.send ipc-renderer (name event) arg)))
