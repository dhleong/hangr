(ns ^{:author "Daniel Leong"
      :doc "Hangouts connection handling"}
  hangr.connection
  (:require [re-frame.core :refer [dispatch]]))

(defonce electron (js/require "electron"))
(defonce ipc (.-ipcRenderer electron))

(def ipc-listeners
  {"connected"
   (fn on-connected[]
     (dispatch [:connected]))})

;; NB: since this isn't really an SPA, we handle
;;  the hangouts connection in the parent process
;;  and communicate over IPC

(defn init!
  []
  (doseq [[event-id handler] ipc-listeners]
    (.removeAllListeners ipc event-id)
    (.on ipc event-id handler)))
