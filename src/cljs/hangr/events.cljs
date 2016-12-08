(ns ^{:author "Daniel Leong"
      :doc "events"}
  hangr.events
  (:require
    [re-frame.core :refer [reg-event-db reg-event-fx inject-cofx path trim-v
                           after debug
                           ->interceptor get-coeffect get-effect 
                           assoc-coeffect assoc-effect]]
    [cljs.spec :as s]
    [hangr.db :refer [default-value]]
    [hangr.util :refer [key->id id->key]]
    [hangr.util.msg :refer [html->msg msg->event]]))


;; -- Interceptors ------------------------------------------------------------
;;

(defn conv-path
  "Like path, but automatically (path)s to a specific
  conversation, indicated by the first argument to the event. 
  The conversation may be an ID, or a conversation map"
  [& extra-path]
  (let [db-store-key :conv-path/db-store ;; this is where, within `context`, we store the original dbs
        conv-path-key :conv-path/path
        get-path (fn [event]
                   (let [conv-or-id (second event)]
                     (concat
                       [:convs (:id conv-or-id conv-or-id)]
                       (flatten extra-path))))]
    (->interceptor
      :id :conv-path
      :before (fn conv-path-before
                [context]
                (let [original-db (get-coeffect context :db)
                      event (get-coeffect context :event)
                      path (get-path event)]
                  (println "ASSOC-COEFFECT" path (get-in original-db path))
                  (-> context
                      (update db-store-key conj original-db)
                      (assoc conv-path-key path)
                      (assoc-coeffect :db (get-in original-db path)))))
      :after (fn conv-path-after
               [context]
               (let [db-store (db-store-key context)
                     path (conv-path-key context)
                     original-db (peek db-store)
                     new-db-store (pop db-store)
                     context' (-> (assoc context db-store-key new-db-store)
                                  (assoc-coeffect :db original-db))     ;; put the original db back so that things like debug work later on
                     db (get-effect context :db ::not-found)]
                 (if (= db ::not-found)
                   context'
                   (do
                     (println "ASSOC-IN" path db)
                     (->> (assoc-in original-db path db)
                          (assoc-effect context' :db)))))))))

;; -- Helpers -----------------------------------------------------------------


;; -- Event Handlers ----------------------------------------------------------

(reg-event-db
  :initialize-db
  (fn [db _]
    (assoc default-value
           :page
           (:page (or db
                      default-value)))))

(reg-event-db
  :navigate
  [trim-v]
  (fn [db page]
    (assoc db :page page)))

(reg-event-db
  :connected
  (fn [db _]
    (assoc db :connecting? false)))

;;
;; Update a conversation. This may trigger
;;  a fetch of people information
(reg-event-fx
  :update-conv
  [trim-v]
  (fn [{:keys [db]} [conv]]
    ;; (println "Update conv" conv)
    ;; (when-not (-> db :people ))
    {:db (assoc-in db 
                   [:convs (:id conv)]
                   conv)
     ;; scroll to bottom if a conversation
     :scroll-to-bottom (= :conv
                          (first (:page db)))}))

(reg-event-db
  :set-self
  [trim-v]
  (fn [db [info]] 
    (assoc db :self info)))

;; -- Events ------------------------------------------------------------------

(reg-event-fx
  :select-conv
  [trim-v]
  (fn [_ [conv-id]]
    {:ipc [:select-conv conv-id]}))

(reg-event-fx
  :send-html
  [(conv-path :events) trim-v]
  (fn [{:keys [db]} [conv-id msg-html]]
    (let [msg (html->msg msg-html)]
      #_(println db, (msg->event msg))
      {:db (concat db [(msg->event msg)])
       :ipc [:send conv-id msg]
       :scroll-to-bottom true})))

(reg-event-fx
  :open-external
  [trim-v]
  (fn [_ [url]]
    {:open-external url}))

(reg-event-fx
  :scroll-to-bottom
  (fn [_ _]
    {:scroll-to-bottom :do!}))
