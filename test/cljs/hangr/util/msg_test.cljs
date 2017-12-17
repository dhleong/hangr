(ns hangr.util.msg-test
  (:require [cljs.test :refer-macros [deftest testing is run-tests]]
            [hangr.util.msg :refer [html->msg]]))

(defn html->parts
  [html]
  (-> html html->msg rest))

(deftest html->msg-test
  (testing "Single line"
    (is (= [[:text "Inara Serra"]] (html->parts "Inara Serra"))))
  (testing "Multi-line"
    (is (= [[:text "Inara Serra"]
            [:newline]
            [:text "Mal Reynolds"]]
           (html->parts "Inara Serra<br/><br/>Mal Reynolds")))
    (is (= [[:text "Inara Serra"]
            [:newline]
            [:text "Mal Reynolds"]]
           (html->parts "<div>Inara Serra</div><div>Mal Reynolds</div>"))))
  (testing "Single link"
    (is (= [[:link "www.serenity.net"]]
           (html->parts "www.serenity.net")))
    (is (= [[:text "Hey guys "]
            [:link "www.serenity.net"]]
           (html->parts "Hey guys www.serenity.net")))
    (is (= [[:text "Hey guys "]
            [:link "www.serenity.net"]
            [:text " is our new site"]]
           (html->parts "Hey guys www.serenity.net is our new site"))))
  (testing "Multi link"
    (is (= [[:link "www.serenity.net"]
            [:text " "]
            [:link "www.alliance.com"]]
           (html->parts "www.serenity.net www.alliance.com")))
    (is (= [[:text "post "]
            [:link "www.serenity.net"]
            [:text " on "]
            [:link "www.alliance.com"]]
           (html->parts "post www.serenity.net on www.alliance.com")))
    (is (= [[:text "post "]
            [:link "www.serenity.net"]
            [:text " on "]
            [:link "www.alliance.com"]
            [:text " guys"]]
           (html->parts "post www.serenity.net on www.alliance.com guys"))))
  (testing "Symbols"
    (is (= [[:text "Let's do this"]]
           (html->parts "Let's do this"))))
  (testing "Entities"
    ;; NOTE: this is not <32> here: it's the actual nbsp character!
    ;;  you can use ga in vim to check!
    (is (= [[:text "Hey "]] (html->parts "Hey&nbsp;")))
    (is (= [[:text "<woah>"]] (html->parts "&lt;woah&gt;"))))
  (testing "Entities in links"
    (is (= [[:link "https://www.youtube.com/watch?v=wflQGe3Bzi0&t=7s&list=WL&index=7"]]
           (html->parts "https://www.youtube.com/watch?v=wflQGe3Bzi0&amp;t=7s&amp;list=WL&amp;index=7"))))
  (testing "Formatting"
    (is (= [[:text "Reynolds" {:bold 1}]
            [:text "Pamphlet"]]
           (html->parts "<b>Reynolds</b>Pamphlet")))
    (is (= [[:text "Reynolds" {:bold 1 :italic 1}]
            [:text " Pamphlet"]]
           (html->parts "<b><i>Reynolds</i></b> Pamphlet")))))

