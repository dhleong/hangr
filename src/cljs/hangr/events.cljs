(ns ^{:author "Daniel Leong"
      :doc "events"}
  hangr.events
  (:require
    [re-frame.core :refer [reg-event-db reg-event-fx reg-cofx inject-cofx 
                           path trim-v after debug
                           ->interceptor get-coeffect get-effect 
                           assoc-coeffect assoc-effect
                           dispatch]]
    [cljs.spec :as s]
    [hangr.db :refer [default-value]]
    [hangr.util :refer [key->id id->key]]
    [hangr.util.conversation :refer [unread?]]
    [hangr.util.msg :refer [html->msg msg->event]]))

;; -- Coeffects ---------------------------------------------------------------
;;

(reg-cofx
   :now
   (fn [coeffects _]
     ;; hangouts uses microseconds, for some reason
      (assoc coeffects :now (* 1000 (.now js/Date)))))

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
                   (->> (assoc-in original-db path db)
                        (assoc-effect context' :db))))))))

(def conv?-scroll
  "Triggers a scroll to the bottom if viewing a covnersation"
  (->interceptor
    :id :conv-scroll
    :after (fn conv-scroll-after
             [context]
             (let [db (get-coeffect context :db)
                   conv? (= :conv
                            (first (:page db)))] 
               ; make SURE we scroll to the bottom,
               ;  even if we render slowly
               (when conv?
                 (js/setTimeout
                   #(dispatch [:scroll-to-bottom])
                   100))
               ; hope for an instant scroll
               (-> context
                   (assoc-effect :scroll-to-bottom conv?))))))

(defn inject-db
  "Create an interceptor that injects a path in the db as a coeffect"
  [path]
  (->interceptor
    :id :inject-db
    :before (fn inject-db-handler
              [context]
              (let [db (get-coeffect context :db)]
                (-> context
                    (assoc-coeffect path (get db path)))))))

(def inject-pending-image
  "Inject the :pending-image var into the context"
  (inject-db :pending-image))

(def inject-self
  "Inject the :self var into the context"
  (inject-db :self))

;; -- Helpers -----------------------------------------------------------------


;; -- Event Handlers ----------------------------------------------------------

(reg-event-db
  :initialize-db
  (fn [db _]
    ;; NOTE: we don't always want to start from scratch
    ;; when developing; some values we should copy over
    ;; if possible
    (let [copy-source (or db
                          default-value)]
      (assoc default-value
             :page (:page copy-source)
             :people (:people copy-source)))))

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
;; Receive a new chat message. This may trigger
;;  a fetch of people information
(reg-event-fx
  :receive-msg
  [inject-self conv?-scroll (conv-path) trim-v]
  (fn [{:keys [db self]} [conv-id msg]]
    (let [conv db] ;; see conv-path
      {:db (update conv :events
                   concat [msg])
       :notify-chat!
       (when-not (or (:focused? db)
                     (= (id->key (:sender_id msg))
                        (:id self)))
         [conv msg])})))

;;
;; Update a conversation. This may trigger
;;  a fetch of people information
(reg-event-fx
  :update-conv
  [conv?-scroll trim-v]
  (fn [{:keys [db]} [conv]]
    (let [updated-db
          (assoc-in db 
                    [:convs (:id conv)]
                    conv)]
      {:db updated-db
       :get-entities (let [known-ids (-> db :people keys set)
                           id-known? (partial contains? known-ids)]
                       (->> conv
                            :members
                            keys
                            (remove id-known?)
                            seq))
       :check-unread (when (= :friends (first (:page db)))
                       (:convs updated-db))})))

;;
;; Update focus status
(reg-event-fx
  :update-focus
  [(inject-cofx :now) (conv-path) trim-v]
  (fn [{:keys [db now]} [conv-id sender-id focused? device]]
    (let [conv db] ;; see conv-path
      {:db (-> conv
               (update-in [:members sender-id]
                          assoc
                          :focused? focused?
                          :device device)
               (update-in [:read-states sender-id :latest-read-timestamp]
                          (fn [old-value]
                            (if focused?
                              now
                              old-value))))})))

;;
;; Update a person
(reg-event-db
  :update-person
  [trim-v]
  (fn [db [person]]
    (assoc-in db 
              [:people (:id person)]
              person)))

;;
;; Update a pending sent message with the fully
;;  inflated version
(reg-event-db
  :update-sent
  [conv?-scroll (conv-path :events) trim-v]
  (fn [events [conv-id sent-msg-event]]
    (let [target-cgid (-> sent-msg-event :self_event_state :client_generated_id)]
      (->> events
           (map 
             (fn [event]
               (if (= (:client-generated-id event)
                      target-cgid)
                 ;; swap in the updated message
                 sent-msg-event
                 ;; return the original
                 event)))
           vec))))

(reg-event-db
  :set-self
  [trim-v]
  (fn [db [info]] 
    (assoc db :self info)))

;; -- Actions -----------------------------------------------------------------

(reg-event-db
  :cancel-image!
  (fn [db]
    (assoc db :pending-image nil)))

(reg-event-fx
  :mark-read!
  [(inject-cofx :now) trim-v]
  (fn [{:keys [db now]} [conv-id]]
    (let [conv (-> db :convs (get conv-id))
          had-unread? (unread? conv)]
      (if had-unread?
        ; update the db eagerly
        (let [updated-db
              (assoc-in db
                        [:convs conv-id :self :latest-read-timestamp]
                        now)]
          ; update the db
          {:db updated-db
           ; send request to mark read
           ; NOTE: the service expects timestamps in milliseconds.
           ; WHY does it return them in microseconds?!
           :ipc [:mark-read! conv-id (/ now 1000)]
           ; check if we should update the unread
           :check-unread (:convs updated-db)})
        ;; no change
        {}))))

(reg-event-fx
  :open-external
  [trim-v]
  (fn [_ [url]]
    {:open-external url}))

(reg-event-fx
  :scroll-to-bottom
  (fn [_ _]
    {:scroll-to-bottom :do!}))

(reg-event-fx
  :select-conv
  [trim-v]
  (fn [_ [conv-id]]
    {:ipc [:select-conv conv-id]
     :dispatch [:mark-read! conv-id]}))

(reg-event-fx
  :send-html
  [conv?-scroll inject-pending-image (conv-path :events) trim-v]
  (fn [{:keys [db pending-image]} [conv-id msg-html]]
    (let [msg (html->msg msg-html)]
      {:db (concat db [(msg->event msg pending-image)])
       :ipc [:send conv-id pending-image msg]
       :dispatch-n (list
                     [:mark-read! conv-id]
                     [:cancel-image!])})))

(reg-event-db
  :send-image
  [trim-v]
  (fn [db [path]]
    (assoc db :pending-image path)))

(reg-event-db
  :sending-msg
  [(conv-path :events) trim-v]
  (fn [events [conv-id image msg]]
    ;; TODO include the image
    (concat events [(msg->event msg)])))

(reg-event-fx
  :set-focused
  [trim-v]
  (fn [{:keys [db]} [focused?]]
    {:db (assoc db :focused? focused?)
     :ipc (let [page (:page db)]
            (println "set-focused" page focused? (:focused? db))
            (when (and (= :conv (first page))
                       (not= (:focused? db) focused?))
              [:set-focused! (second page) focused?]))}))

;; handle a local typing event
(reg-event-fx
  :typing!
  [trim-v]
  (fn [_ [conv-id & [stop?]]]
    {:typing! [conv-id stop?]}))

;; update the typing status for another user in a conversation
(reg-event-db
  :update-typing
  [(conv-path :members) trim-v]
  (fn [members [conv-id user-id status]]
    (-> members
        (assoc-in [user-id :typing]
                   status))))

(reg-event-db
  :update-watermark
  [(conv-path :read-states) trim-v]
  (fn [read-states [conv-id sender-id latest-read-timestamp]]
    (let [updated (assoc-in
                    read-states
                    [sender-id :latest-read-timestamp]
                    latest-read-timestamp)]
      (.log js/console "Updated: " sender-id updated)
      updated)))
