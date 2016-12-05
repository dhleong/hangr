(ns ^{:author "Daniel Leong"
      :doc "Conversation (chat message list) view"}
  hangr.views.conversation
  (:require [clojure.string :as string]
            [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util :refer [click-dispatch]]))

;; -- Attachment Types --------------------------------------------------------

(defn attachment-image
  [attachment]
  [:img.attachment
   {:src (-> attachment :plus_photo :data :thumbnail :image_url)
    :style {:max-width "100%"}}])


;; -- Segment Types -----------------------------------------------------------

(defn segment-text
  [segment]
  ;; TODO formatting
  (when-let [formatting (:formatting segment)]
    (println "TODO: format:" formatting))
  [:span.segment.text (:text segment)])

(defn segment-link
  [segment]
  (let [url (-> segment :link_data :link_target)]
    [:a.segment.link
     {:href url
      :on-click (click-dispatch [:open-external url])}
     (:text segment)]))

;; -- Conversation Item Facade ------------------------------------------------

(defn conversation-item
  [event]
  [:div.event-item
   (let [content (-> event :chat_message :message_content)
         event-id (:id event)] 
     (concat
       (for [[i attachment] (map-indexed list (:attachment content))]
         (with-meta
           (let [embed-item (:embed_item attachment)]
             (cond
               (:plus_photo embed-item) [attachment-image embed-item]
               :else [:span (str "UNKNOWN ATTACHMENT:" embed-item)]))
           ;; we can't use the reader macro since it's coming
           ;;  from a (case)
           {:key (str event-id "a" i)} ))
       (for [[i segment] (map-indexed list (:segment content))]
         (with-meta
           (case (:type segment)
             "TEXT" [segment-text segment]
             "LINK" [segment-link segment]
             [:span (str "UNKNOWN SEGMENT:" segment)])
           ;; see above
           {:key (str event-id "s" i)}))))])

;; -- Main Interface ----------------------------------------------------------

(defn conversation
  [id]
  (let [conv (subscribe [:conv id])]
    (fn []
      (let [conv @conv
            member-map
            (->> conv
                 :members
                 (map (fn [m] [(:id m) m]))
                 (into {}))]
        [:div#conversation
         [:ul#events
          (for [event (:events conv)]
            ^{:key (:id event)} [:li.event
                                 {:class (if (:incoming? event)
                                           "incoming"
                                           "outgoing")}
                                 [conversation-item event]])]]))))

(defn conversation-title
  [id-or-conv]
  (let [conv (if (string? id-or-conv)
               (subscribe [:conv id-or-conv])
               (atom id-or-conv))
        self (subscribe [:self])]
    (fn []
      (let [conv @conv]
        [:span 
         (or
           ; prefer the chosen name, if there is one
           (:name conv)
           ; otherwise, join all the other members
           (clojure.string/join 
             ", "
             (->> conv 
                  :members
                  ; remove ourself...
                  (remove #(= (:id @self)
                              (:id %)))
                  ; get the name
                  (map :name))))]))))
