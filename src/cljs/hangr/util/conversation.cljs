(ns ^{:author "Daniel Leong"
      :doc "Conversation utilities"}
  hangr.util.conversation
  (:require [hangr.util :refer [id->key join-sorted-by]]))

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

(defn conv-latest-read
  "Get a usable latest-read timestamp for a participant
  in a conversation"
  [conv person-id]
  (let [given (->> conv 
                   :read-states
                   person-id
                   :latest-read-timestamp)]
    (if (and (number? given)
             (> given 0))
      ; easy case; we actually know
      given
      ; timestamp = 0 means "unchanged"; find the most recent event where they're the sender
      (or (->> conv 
               :events
               (filter #(= person-id
                           (:sender %)))
               last
               :timestamp)
          ; fall back to 0 if it was just nil
          0))))

(defn fill-members
  "Given a conv and a map of id -> person `people`,
  fill out the :members array to include as much
  info about each member as possible"
  [people conv]
  (update 
    conv
    :members
    (fn [members-map]
      (->> members-map
           (map
             (fn [[person-id member]]
               (let [person (get people person-id)]
                 {person-id
                  (assoc
                    (if (:name person) ; don't override with worse info
                      (merge member person)
                      member)
                    ; also, insert their latest-read-timestamp
                    :latest-read-timestamp
                    (conv-latest-read
                      conv
                      person-id))})))
           (into {})))))

(defn generate-read-events
  "Given a conv whose :members array has been updated
  via (fill-members), generate read events to be 
  interleaved into (:events conv)"
  [conv]
  (let [self-id (-> conv :self :id)
        members (->> (:members conv)
                     vals
                     (remove (comp (partial = self-id) :id))
                     (sort-by (comp long :latest-read-timestamp)))]
    (map 
      (fn [member]
        {:sender (:id member)
         :hangr-type :read-indicator
         :id (str (name (:id member)) "-read")
         :typing (:typing member)
         :timestamp (:latest-read-timestamp member)})
      members)))

(defn generate-timestamps
  "Given a conv, generate timestamp events to be interleaved
  into (:events conv)"
  [conv]
  ;; TODO FIXME merge timestamps within a minute or so
  (->> conv
       :events
       (map (fn [e]
              (-> e
                  (select-keys [:sender :timestamp :incoming?])
                  (assoc
                    :hangr-type :timestamp
                    :id (str "stamp-" (:timestamp e))))))))

(defn insert-hangr-events
  "Inserts special events into :events for rendering convenience"
  [conv]
  (update-in 
    conv
    [:events]
    (partial join-sorted-by (comp long :timestamp))
    ; collections to interleave:
    (generate-timestamps conv)
    (generate-read-events conv)))

(defn plus-photo-data
  "Extract the :plus_photo :data field from an embed-item
  (or an attachment), 
  if possible. Sometimes it's returned in weird places"
  [embed-item]
  (let [embed-item (or (:embed_item embed-item)
                       embed-item)]
    (or (-> embed-item :plus_photo :data)
        (:embeds.PlusPhoto.plus_photo embed-item)
        ; wacky, protobuf-like version:
        (when (= [249] (-> embed-item :type_))
          (let [data (-> embed-item :data vals first)
                thumbnail-raw (first data)
                thumbnail-url (-> thumbnail-raw (nth 0))
                thumbnail-image (-> thumbnail-raw (nth 1))
                thumbnail-width (-> thumbnail-raw (nth 2))
                thumbnail-height (-> thumbnail-raw (nth 3))
                url (-> data (nth 4))]
            {:url url
             :thumbnail
             {:image_url thumbnail-image
              :url thumbnail-url
              :width_px thumbnail-width
              :height_px thumbnail-height}})))))

(defn scale-photo
  [photo-data max-width]
  (when-let [w (-> photo-data :thumbnail :width_px)]
    (when-let [h (-> photo-data :thumbnail :height_px)]
      (let [r (/ h w)]
        [max-width (* r max-width)]))))

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
