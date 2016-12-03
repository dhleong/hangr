(ns ^{:author "Daniel Leong"
      :doc "Hangouts connection handling"}
  hangr.connection
  (:require [re-frame.core :refer [dispatch]]
            [hangr.util :refer [js->real-clj]]))

(defonce electron (js/require "electron"))
(defonce ipc (.-ipcRenderer electron))

(defn conv->clj
  "Clean up a js conv object to something sane"
  [js-conv]
  (-> js-conv
      js->real-clj
      (as-> c 
        (assoc c 
               :id 
               (or (:conversation_id c)
                   (-> c :conversation :conversation_id :id))))
      ;; TODO etc.
      ))

(def ipc-listeners
  {:connected
   (fn on-connected[]
     (println "Connected!")
     (dispatch [:connected]))
   :recent-conversations
   (fn [_ convs]
     (.log js/console "Got conversations" convs)
     (doseq [conv (map conv->clj convs)]
       (dispatch [:update-conv conv])))})

;; NB: since this isn't really an SPA, we handle
;;  the hangouts connection in the parent process
;;  and communicate over IPC

(defn init!
  []
  (.send ipc "request-status")
  (doseq [[event-id handler] ipc-listeners]
    (let [event-id (name event-id)] 
      (.removeAllListeners ipc event-id)
      (.on ipc event-id handler))))
