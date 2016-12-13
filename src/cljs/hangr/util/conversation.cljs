(ns ^{:author "Daniel Leong"
      :doc "Conversation utilities"}
  hangr.util.conversation)

(defn event-incoming?
  "Given the user's (:self) id and an event,
  return true if that event is incoming"
  [self-id event]
  (and (not= self-id
             (-> event :sender))
       (not (:client-generated-id event))))

(defn conv-event-incoming?
  "Given a conversation and an event in that
  conversation, return true if that event is incoming"
  [conv event]
  (let [self-id (-> conv :self :id)]
    (event-incoming? self-id event)))

(defn fill-members
  "Given a conv and a map of id -> person `people`,
  fill out the :members array to include as much
  info about each member as possible"
  [people conv]
  (update 
    conv
    :members
    (partial
      map
      (fn [member]
        (let [person (get people (:id member))]
          (if (:name person) ;; don't override with worse info
            person
            member))))))

(defn unread?
  "Check if the conversation is unread"
  [conv]
  (when-let [latest-read (-> conv :self :latest-read-timestamp)]
    (->> conv
         :events
         ; only incoming events matter
         (filter (partial conv-event-incoming? conv))
         ; hangout events don't count
         (remove :hangout_event)
         ; find any whose timestamp is after our latest read
         (some #(> (:timestamp %) latest-read)))))
