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
           (html->parts "Inara Serra<br/><br/>Mal Reynolds"))))
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
  (testing "Entities"
    (is (= [[:text "Hey "]] (html->parts "Hey&nbsp;")))))

