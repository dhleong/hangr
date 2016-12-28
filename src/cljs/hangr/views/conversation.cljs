(ns ^{:author "Daniel Leong"
      :doc "Conversation (chat message list) view"}
  hangr.views.conversation
  (:require [clojure.string :as string]
            [reagent.core  :as reagent]
            [re-frame.core :refer [subscribe dispatch]]
            [hangr.util :refer [id->key]]
            [hangr.util.ui :refer [click-dispatch]]
            [hangr.util.conversation :refer [plus-photo-data scale-photo]]
            [hangr.views.widgets :refer [avatar typing-indicator]]))

;; this is hax, measured with devtools; helps to prevent gross
;; pops with image attachments, though
(def max-photo-width 204)

;; -- Utility functions -------------------------------------------------------

(defn- anything-focused?
  []
  (let [sel (.getSelection js/window)
        range (when (> (.-rangeCount sel) 0)
                (.getRangeAt sel 0))]
    (when range
      (> (- (.-endOffset range)
            (.-startOffset range))
         0))))

(defn- focus!
  []
  (-> js/document
      (.getElementById "composer-input")
      (.focus)))

(defn- image?
  [path]
  (contains?
    #{".jpg" ".png" "jpeg"} ; TODO etc
    (string/lower-case 
      (subs path (- (count path) 4)))))

(defn- sticker?
  "Returns truthy if the embed-item is a sticker"
  [embed-item]
  (let [photo (plus-photo-data embed-item)]
    (and
      ; it must at least be a photo...
      photo
      ; is this sufficient? seems sketchy
      (nil? (-> photo
                :thumbnail
                :url)))))

(defn- submit-event?
  "Check if the key press event is a submit event"
  [e]
  (and (= "Enter" (.-key e))
       (not (.-shiftKey e))))

;; -- Attachment Types --------------------------------------------------------

(defn attachment-image
  [attachment]
  (let [sticker? (sticker? attachment)
        photo-data (plus-photo-data attachment)
        scaled-size (scale-photo photo-data max-photo-width)
        url (:url photo-data)
        styling (when-let [[w h] scaled-size]
                  {:min-height h})
        img [:img.attachment
             {:src (-> photo-data :thumbnail :image_url)
              :class (if sticker?
                       "sticker"
                       "image")
              :style styling}]]
    (if (and sticker? url)
      img
      [:a
       {:href url
        :on-click (click-dispatch [:open-external url])}
       img])))

(defn attachment-google-voice
  [attachment]
  ; for these we have a :data field that looks like:
  ;   gv-0123456789012344567
  ; It'd be awesome if we could figure out how to load the audio
  [:div.segment.text "[Unsupported: Google Voice Voicemail]"])

;; -- Segment Types -----------------------------------------------------------

(defn segment-link
  [segment]
  (let [url (-> segment :link_data :link_target)]
    [:a.segment.link
     {:href url
      :on-click (click-dispatch [:open-external url])}
     (:text segment)]))

(defn segment-linebreak
  []
  [:p])

(defn segment-text
  [segment]
  ;; TODO formatting
  (when-let [formatting (:formatting segment)]
    (println "TODO: format:" formatting))
  [:span.segment.text (:text segment)])

;; -- Hangout Events ----------------------------------------------------------

(defn hangout-event-end
  [member-map self event hangouts-ev]
  (let [participant-ids (->> hangouts-ev :participant_id (map id->key) set)
        self-id (:id self)]
    (if (contains?
          participant-ids
          self-id)
      [:div "📞 You were in a call with "
       (->> participant-ids
            (remove (partial = self-id))
            (map (comp :name member-map))
            (string/join ", "))]
      [:div "✖️ You missed a call from "
       (->> event :sender_id id->key member-map :name)])))

;; -- Special Hangr-only Events -----------------------------------------------

(defn hangr-read-indicator
  [member-map event]
  (let [member (get member-map (:sender event))]
    (.log js/console "MEMBER = " member)
    [:div.event.read-indicator
     [avatar 
      {:class 
       (if (:focused? member)
         "focused"
         "inactive")} 
      member]
     ; typing state:
     (let [typing (:typing member)]
       (when-not (or (nil? typing)
                     (= :stopped typing))
         [typing-indicator typing]))]))

(defn hangr-event
  [member-map event]
  (case (:hangr-type event)
    :read-indicator [hangr-read-indicator member-map event]))

;; -- Conversation Item Facade ------------------------------------------------

(defn conversation-item
  [event]
  (let [content (-> event :chat_message :message_content)
        event-id (:id event)]
    [:div.event-item
     {:class (when (-> content :attachment first :embed_item sticker?)
               "has-sticker")}
     (concat
       (for [[i attachment] (map-indexed list (:attachment content))]
         (with-meta
           (let [embed-item (:embed_item attachment)]
             (cond
               (plus-photo-data embed-item) [attachment-image embed-item]
               (= [438] (-> embed-item :type_)) [attachment-google-voice embed-item]
               :else [:span (str "UNKNOWN ATTACHMENT:" embed-item)]))
           ;; we can't use the reader macro since it's coming
           ;;  from a (case)
           {:key (str event-id "a" i)} ))
       (for [[i segment] (map-indexed list (:segment content))]
         (with-meta
           (case (:type segment)
             "TEXT" [segment-text segment]
             "LINK" [segment-link segment]
             "LINE_BREAK" [segment-linebreak]
             [:span (str "UNKNOWN SEGMENT:" segment)])
           ;; see above
           {:key (str event-id "s" i)})))]))

;; -- Conversation Events -----------------------------------------------------

(defn conversation-events
  "Conversation event list, broken out from (conversation) to avoid
  unnecessary redraws of the composer (since it doesn't actually depend
  on any of the stuff this thing subscribes to)"
  [id]
  (let [conv (subscribe [:conv id])
        self (subscribe [:self])]
    (fn []
      (let [conv @conv
            self @self
            member-map (:members conv)]
        [:ul#events
         (for [event (:events conv)]
           (cond
             ; hangout event:
             (not (nil? (:hangout_event event)))
             (let [hangouts-ev (:hangout_event event)]
               (when (= "END_HANGOUT" (:event_type hangouts-ev))
                 ^{:key (:id event)} [:li.hangout.event
                                      [hangout-event-end member-map self event hangouts-ev]]))
             ; special hangr thing
             (not (nil? (:hangr-type event)))
             ^{:key (:id event)} [hangr-event member-map event]
             ; default:
             :else
             (let [class-name 
                   (->>
                     [(if (:incoming? event)
                        "incoming"
                        "outgoing")
                      (when (:client-generated-id event)
                        "pending")]
                     (filter identity)
                     (string/join " "))]
               ^{:key (:id event)} 
               [:li.event
                {:class class-name}
                (when (:incoming? event)
                  [avatar (get member-map (:sender event))])
                [conversation-item event]])))]))))

;; -- File drag-and-drop Receiver ---------------------------------------------

(defn file-drop-receiver
  [id]
  (let [toggle-class
        (fn [class-name add? e]
          (.preventDefault e)
          (-> e
              .-target
              .-classList
              (.toggle class-name add?)))] 
    [:div#drop-receiver.drop-receiver
     {:on-drag-enter (partial toggle-class "dragover" :add!)
      :on-drag-leave (partial toggle-class "dragover" false)
      :on-drag-over #(.preventDefault %)
      :on-drop
      (fn [e]
        ; remove dragover class and preventDefault on the event
        (toggle-class "dragover" false e)
        (let [el (.-target e)]
          ; make EXTRA SURE it's removed
          (js/setTimeout
            #(.toggle (.-classList el) "dragover" false)
            20))
        (let [files (-> e
                       .-dataTransfer
                       .-files)
              path (-> files
                       (aget 0)
                       .-path)]
          (cond
            (nil? path)
            nil ;; ignore
            ;
            (> (.-length files) 1)
            (println "WARN: too many files") ;; TODO snackbar-ish?
            ;
            (not (image? path))
            (println "WARN: not an image") ;; TODO snackbar-ish?
            ;
            :else
            (dispatch [:send-image path]))))}
     ; TODO: something a bit nicer
     [:div.label 
      "Drop here to send!"]]))

;; -- Pending image overlay ---------------------------------------------------

(defn pending-image
  [id]
  (let [img (subscribe [:pending-image])]
    (fn []
      [:div#pending-image-container
       (when-let [img @img]
         [:div.pending-image
          [:div.delete-button
           {:on-click (click-dispatch [:cancel-image!])}
           "✖️"]
          [:img
           {:src img}]
          [:div.send-button-container
           {:on-click (click-dispatch [:send-html id ""])}
           [:i.fa.fa-paper-plane.send-button]]])])))

;; -- Main Interface ----------------------------------------------------------

(defn composer
  "The 'composer,' the input box"
  [id]
  [:div#composer
   [:div#composer-input.input
    {:content-editable true
     :placeholder "Message"
     :on-key-press
     (fn [e]
       (when (submit-event? e)
         ; don't create a newline, please:
         (.preventDefault e)
         (let [el (.-target e)
               raw-message (.-innerHTML el)]
           ; clear the input box
           (set! (.-innerHTML el) "")
           ; eagerly trigger typing stopped event
           (dispatch [:typing! id :stop!])
           ; dispatch the event
           (dispatch [:send-html id raw-message]))))
     :on-key-up
     (fn [e]
       (when-not (submit-event? e)
         (let [raw-message (.-innerHTML (.-target e))]
           ;; if the input is empty, also stop typing
           ;; otherwise, just dispatch a typing event
           (dispatch [:typing! id (when (empty? raw-message)
                                    :stop!)]))))}]])

(defn conversation
  [id]
  (reagent/create-class
    {:display-name "conversation"
     :component-did-mount focus!
     :reagent-render
     (fn [id]
       [:div#conversation
        [file-drop-receiver]
        [:div#events-container.scroll-host
         {:on-click 
          (fn [e]
            (when-not (anything-focused?)
              (focus!)))}
         [conversation-events id]]
        [pending-image id]
        [composer id]])}))

(defn conversation-title
  [id-or-conv]
  (let [conv (if (string? id-or-conv)
               (subscribe [:conv id-or-conv])
               (atom id-or-conv))]
    (fn []
      (let [conv @conv
            self-id (-> conv :self :id)]
        [:span 
         (or
           ; prefer the chosen name, if there is one
           (:name conv)
           ; otherwise, join all the other members
           (clojure.string/join 
             ", "
             (->> conv 
                  :members
                  vals
                  ; remove ourself...
                  (remove #(= self-id
                              (:id %)))
                  ; get the name
                  (map :name))))]))))
