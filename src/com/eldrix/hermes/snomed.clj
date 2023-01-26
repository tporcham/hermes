; Copyright 2020 Mark Wardle and Eldrix Ltd
;
;   Licensed under the Apache License, Version 2.0 (the "License");
;   you may not use this file except in compliance with the License.
;   You may obtain a copy of the License at
;
;       http://www.apache.org/licenses/LICENSE-2.0
;
;   Unless required by applicable law or agreed to in writing, software
;   distributed under the License is distributed on an "AS IS" BASIS,
;   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;   See the License for the specific language governing permissions and
;   limitations under the License.
;;;;
(ns com.eldrix.hermes.snomed
  "Package snomed defines the specification for SNOMED-CT releases in the RF2
  format.

  See the [release file specifications](https://confluence.ihtsdotools.org/display/DOCRELFMT/SNOMED+CT+Release+File+Specifications)

  These are, in large part, raw representations of the release files with some
  small additions, predominantly relating to valid enumerations, to aid
  computability.

  These structures are designed to cope with importing any SNOMED-CT
  distribution, including full distributions, a snapshot or a delta.

  * Full	   The files representing each type of component contain every version
             of every component ever released.
  * Snapshot The files representing each type of component contain one version
             of every component released up to the time of the snapshot. The
             version of each component contained in a snapshot is the most
             recent version of that component at the time of the snapshot.
  * Delta	   The files representing each type of component contain only
             component versions created since the previous release. Each
             component version in a delta release represents either a new
             component or a change to an existing component."

  (:require [clojure.core.match :as match]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [clojure.string :as str]
            [clojure.tools.logging.readable :as log]
            [com.eldrix.hermes.verhoeff :as verhoeff])
  (:import [java.time LocalDate]
           [java.time.format DateTimeFormatter DateTimeParseException]
           (java.io File)
           (java.util UUID)
           (com.eldrix.hermes.sct IAssociationRefsetItem IAtttributeValueRefsetItem IComplexMapRefsetItem IConcept IDescription IExtendedConcept IExtendedMapRefsetItem ILanguageRefsetItem IModuleDependencyRefsetItem IOWLExpressionRefsetItem IRefsetDescriptorRefsetItem IResult ISimpleMapRefsetItem ISimpleRefsetItem)))

(defmulti ->vec "Turn a SNOMED entity into a vector" type)

(defn parse-date ^LocalDate [^String s] (try (LocalDate/parse s (DateTimeFormatter/BASIC_ISO_DATE)) (catch DateTimeParseException _)))
(defn parse-bool ^Boolean [^String s] (if (= "1" s) true false))
(defn unsafe-parse-uuid ^UUID [^String s] (UUID/fromString s))

(defmulti unparse "Export data as per the SNOMED RF2 file format specification." type)
(defmethod unparse LocalDate [v] (.format (DateTimeFormatter/BASIC_ISO_DATE) v))
(defmethod unparse Boolean [v] (if v "1" "0"))
(defmethod unparse Long [v] (str v))
(defmethod unparse Integer [v] (str v))
(defmethod unparse String [v] v)
(defmethod unparse UUID [v] (.toString ^UUID v))
(defmethod unparse :info.snomed/Component [v] (mapv unparse (->vec v)))

;; The core SNOMED entities are Concept, Description and Relationship.
(defrecord Concept
  [^long id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long definitionStatusId]
  IConcept
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (definitionStatusId [_] moduleId))

(defrecord Description
  [^long id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long conceptId
   ^String languageCode
   ^long typeId
   ^String term
   ^long caseSignificanceId]
  IDescription
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (moduleId [_] moduleId)
  (conceptId [_] conceptId)
  (languageCode [_] languageCode)
  (typeId [_] typeId)
  (term [_] term)
  (caseSignificanceId [_] caseSignificanceId))

(defrecord Relationship
  [^long id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long sourceId
   ^long destinationId
   ^long relationshipGroup
   ^long typeId
   ^long characteristicTypeId
   ^long modifierId])

;; ReferenceSet support customization and enhancement of SNOMED CT content. These include representation of subsets,
;; language preferences maps for or from other code systems. There are multiple reference set types which extend
;; this structure. We have concrete versions of the most useful reference set types permitting hand-crafted
;; serialization and straightforward consumption by typed clients such as java.

;; RefSetDescriptorRefsetItem is a type of reference set that provides information about a different reference set
;; See https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.11+Reference+Set+Descriptor
;; It provides the additional structure for a given reference set.
(defrecord RefsetDescriptorRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId
   ^long referencedComponentId
   ^long attributeDescriptionId
   ^long attributeTypeId
   ^int attributeOrder]
  IRefsetDescriptorRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (attributeDescriptionId [_] attributeDescriptionId)
  (attributeTypeId [_] attributeTypeId)
  (attributeOrder [_] attributeOrder)
  (fields [_] []))


;; SimpleReferenceSet is a simple reference set usable for defining subsets
;; See https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.1+Simple+Reference+Set
(defrecord SimpleRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId
   ^long referencedComponentId
   fields]
  ISimpleRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (fields [_] fields))

;; An Association reference set is a reference set used to represent associations between components
(defrecord AssociationRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId
   ^long referencedComponentId
   ^long targetComponentId
   fields]
  IAssociationRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (targetComponentId [_] targetComponentId)
  (fields [_] fields))

;; LanguageReferenceSet is a A 900000000000506000 |Language type reference set| supporting the representation of
;; language and dialects preferences for the use of particular descriptions.
;; "The most common use case for this type of reference set is to specify the acceptable and preferred terms
;; for use within a particular country or region. However, the same type of reference set can also be used to
;; represent preferences for use of descriptions in a more specific context such as a clinical specialty,
;; organization or department."
;;
;; No more than one description of a specific description type associated with a single concept may have the
;; acceptabilityId value 900000000000548007 |Preferred|.
;; Every active concept should have one preferred synonym in each language.
;; This means that a language reference set should assign the acceptabilityId  900000000000548007 |Preferred|
;; to one  synonym (a  description with  typeId value 900000000000013009 |synonym|) associated with each concept.
;; This description is the preferred term for that concept in the specified language or dialect.
;; Any  description which is not referenced by an active row in the   reference set is regarded as unacceptable
;;(i.e. not a valid  synonym in the language or  dialect ).
;; If a description becomes unacceptable, the relevant language reference set member is inactivated by adding a new
;; row with the same id, the effectiveTime of the change and the value active=0.
;; For this reason there is no requirement for an "unacceptable" value.
;; See https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.4+Language+Reference+Set
;; - acceptabilityId is a subtype of 900000000000511003 |Acceptability| indicating whether the description is acceptable
;; or preferred for use in the specified language or dialect .
(defrecord LanguageRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId
   ^long referencedComponentId
   ^long acceptabilityId
   fields]
  ILanguageRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (acceptabilityId [_] acceptabilityId)
  (fields [_] fields))


;; SimpleMapReferenceSet is a straightforward one-to-one map between SNOMED-CT concepts and another
;; coding system. This is appropriate for simple maps.
;; See https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.9+Simple+Map+Reference+Set
(defrecord SimpleMapRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId
   ^long referencedComponentId
   ^String mapTarget
   fields]
  ISimpleMapRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (mapTarget [_] mapTarget)
  (fields [_] fields))


;; ComplexMapReferenceSet represents a complex one-to-many map between SNOMED-CT and another
;; coding system.
;; A 447250001 |Complex map type reference set|enables representation of maps where each SNOMED
;; CT concept may map to one or more codes in a target scheme.
;; The type of reference set supports the general set of mapping data required to enable a
;; target code to be selected at run-time from a number of alternate codes. It supports
;; target code selection by accommodating the inclusion of machine readable rules and/or human readable advice.
(defrecord ComplexMapRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId
   ^long referencedComponentId
   ^long mapGroup                                           ;; An Integer, grouping a set of complex map records from which one may be selected as a target code.
   ^long mapPriority                                        ;; Within a mapGroup, the mapPriority specifies the order in which complex map records should be checked
   ^String mapRule                                          ;; A machine-readable rule, (evaluating to either 'true' or 'false' at run-time) that indicates whether this map record should be selected within its mapGroup.
   ^String mapAdvice                                        ;; Human-readable advice, that may be employed by the software vendor to give an end-user advice on selection of the appropriate target code from the alternatives presented to him within the group.
   ^String mapTarget                                        ;; The target code in the target terminology, classification or code system.
   ^long correlationId                                      ;; A child of 447247004 |SNOMED CT source code to target map code correlation value|in the metadata hierarchy, identifying the correlation between the SNOMED CT concept and the target code.
   fields]
  IComplexMapRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (mapGroup [_] mapGroup)
  (mapPriority [_] mapPriority)
  (mapRule [_] mapRule)
  (mapAdvice [_] mapAdvice)
  (mapTarget [_] mapTarget)
  (correlationId [_] correlationId)
  (fields [_] fields))


;; An 609331003 |Extended map type reference set|adds an additional field to allow categorization of maps.
;; https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.10+Complex+and+Extended+Map+Reference+Sets
(defrecord ExtendedMapRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId
   ^long referencedComponentId
   ^long mapGroup                                           ;; An Integer, grouping a set of complex map records from which one may be selected as a target code.
   ^long mapPriority                                        ;; Within a mapGroup, the mapPriority specifies the order in which complex map records should be checked
   ^String mapRule                                          ;; A machine-readable rule, (evaluating to either 'true' or 'false' at run-time) that indicates whether this map record should be selected within its mapGroup.
   ^String mapAdvice                                        ;; Human-readable advice, that may be employed by the software vendor to give an end-user advice on selection of the appropriate target code from the alternatives presented to him within the group.
   ^String mapTarget                                        ;; The target code in the target terminology, classification or code system.
   ^long correlationId                                      ;; A child of 447247004 |SNOMED CT source code to target map code correlation value|in the metadata hierarchy, identifying the correlation between the SNOMED CT concept and the target code.
   ^long mapCategoryId                                      ;; Identifies the SNOMED CT concept in the metadata hierarchy which represents the MapCategory for the associated map member.
   fields]
  IExtendedMapRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (mapGroup [_] mapGroup)
  (mapPriority [_] mapPriority)
  (mapRule [_] mapRule)
  (mapAdvice [_] mapAdvice)
  (mapTarget [_] mapTarget)
  (correlationId [_] correlationId)
  (mapCategoryId [_] mapCategoryId)
  (fields [_] fields))

;; AttributeValueReferenceSet provides a way to associate arbitrary attributes with a SNOMED-CT component
;; See https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.3+Attribute+Value+Reference+Set
(defrecord AttributeValueRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId
   ^long referencedComponentId                              ;;
   ^long valueId
   fields]
  IAtttributeValueRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (valueId [_] valueId)
  (fields [_] fields))

;; The Module dependency reference set is used to represent dependencies between modules, taking account of module versioning.
;; see https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.4.2+Module+Dependency+Reference+Set
(defrecord ModuleDependencyRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId                                           ;; always 900000000000534007
   ^long referencedComponentId                              ;; The value of this attribute is the id of the module on which the source module (referred to by the moduleId attribute) is dependent.
   ^LocalDate sourceEffectiveTime                           ;; The effective time of the dependent source module (identified by moduleId). This specifies a version of that module, consisting of all components that have the same moduleId as this refset member in their states as at the specified targetEffectiveTime .
   ^LocalDate targetEffectiveTime                           ;; The effective time of the target module required to satisfy the dependency (identified by referencedComponentId). This specifies a version of that module, consisting of all components with the moduleId specified by referencedComponentId in their states as at the specified targetEffectiveTime.
   fields]
  IModuleDependencyRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (sourceEffectiveTime [_] sourceEffectiveTime)
  (targetEffectiveTime [_] targetEffectiveTime)
  (fields [_] fields))

;; OWLExpressionRefsetItem provides a way of linking an OWL expression to every SNOMED CT component.
;; see https://confluence.ihtsdotools.org/display/REUSE/OWL+Expression+Reference+Set
(defrecord OWLExpressionRefsetItem
  [^UUID id
   ^LocalDate effectiveTime
   ^boolean active
   ^long moduleId
   ^long refsetId                                           ;; a subtype descendant of: 762676003 |OWL expression type reference set (foundation metadata concept)
   ^long referencedComponentId
   ^String owlExpression
   fields]
  IOWLExpressionRefsetItem
  (id [_] id)
  (effectiveTime [_] effectiveTime)
  (active [_] active)
  (moduleId [_] moduleId)
  (refsetId [_] refsetId)
  (referencedComponentId [_] referencedComponentId)
  (owlExpression [_] owlExpression))

;; An extended concept is a denormalised representation of a single concept bringing together all useful data into one
;; convenient structure, that can then be cached and used for inference.
(defrecord ExtendedConcept
  [^Concept concept
   descriptions
   parentRelationships
   directParentRelationships
   refsets]
  IExtendedConcept
  (id [_] (.-id concept))
  (effectiveTime [_] (.-effectiveTime concept))
  (active [_] (.-active concept))
  (moduleId [_] (.-moduleId concept))
  (definitionStatusId [_] (.-definitionStatusId concept))
  (descriptions [_] descriptions)
  (parentRelationships [_] parentRelationships)
  (directParentRelationships [_] directParentRelationships)
  (refsets [_] refsets))

(defn parse-concept [v]
  (->Concept
    (Long/parseLong (v 0))
    (parse-date (v 1))
    (parse-bool (v 2))
    (Long/parseLong (v 3))
    (Long/parseLong (v 4))))

(defmethod ->vec Concept [c]
  [(:id c) (:effectiveTime c) (:active c) (:moduleId c) (:definitionStatusId c)])

(defn parse-description [v]
  (->Description
    (Long/parseLong (v 0))
    (parse-date (v 1))
    (parse-bool (v 2))
    (Long/parseLong (v 3))
    (Long/parseLong (v 4))
    (v 5)
    (Long/parseLong (v 6))
    (v 7)
    (Long/parseLong (v 8))))

(defmethod ->vec Description [d]
  [(:id d) (:effectiveTime d) (:active d) (:moduleId d) (:conceptId d)
   (:languageCode d) (:typeId d) (:term d) (:caseSignificanceId d)])

(defn parse-relationship [v]
  (->Relationship
    (Long/parseLong (v 0))
    (parse-date (v 1))
    (parse-bool (v 2))
    (Long/parseLong (v 3))                                  ;; moduleId
    (Long/parseLong (v 4))                                  ;; sourceId
    (Long/parseLong (v 5))                                  ;; destinationId
    (Long/parseLong (v 6))                                  ;; relationshipGroup
    (Long/parseLong (v 7))                                  ;; typeId
    (Long/parseLong (v 8))                                  ;; characteristicTypeId
    (Long/parseLong (v 9))))                                ;; modifierId

(defmethod ->vec Relationship [r]
  [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:sourceId r) (:destinationId r)
   (:relationshipGroup r) (:typeId r) (:characteristicTypeId r) (:modifierId r)])

(def refset-standard-patterns
  {:info.snomed/RefsetDescriptorRefset "cci"
   :info.snomed/SimpleRefset           ""
   :info.snomed/AssociationRefset      "c"
   :info.snomed/LanguageRefset         "c"
   :info.snomed/SimpleMapRefset        "s"
   :info.snomed/ComplexMapRefset       "iisssc"
   :info.snomed/ExtendedMapRefset      "iissscc"
   :info.snomed/AttributeValueRefset   "i"
   :info.snomed/OWLExpressionRefset    "s"})

(defn- reify-association-refset-item [item]
  (let [[target & more] (:fields item)]
    (map->AssociationRefsetItem (assoc item :targetComponentId target :fields (or more [])))))
(defn- reify-language-refset-item [item]
  (let [[acceptability & more] (:fields item)]
    (map->LanguageRefsetItem (assoc item :acceptabilityId acceptability :fields (or more [])))))
(defn- reify-simple-map-refset-item [item]
  (let [[map-target & more] (:fields item)]
    (map->SimpleMapRefsetItem (assoc item :mapTarget map-target :fields (or more [])))))
(defn- reify-extended-map-refset-item [item]
  (let [[mapGroup mapPriority mapRule mapAdvice mapTarget correlationId mapCategoryId & more] (:fields item)]
    (map->ExtendedMapRefsetItem (assoc item
                                  :mapGroup mapGroup :mapPriority mapPriority
                                  :mapRule mapRule :mapAdvice mapAdvice
                                  :mapTarget mapTarget :correlationId correlationId
                                  :mapCategoryId mapCategoryId :fields (or more [])))))
(defn- reify-complex-map-refset-item [item]
  (let [[mapGroup mapPriority mapRule mapAdvice mapTarget correlationId & more] (:fields item)]
    (map->ComplexMapRefsetItem (assoc item
                                 :mapGroup mapGroup :mapPriority mapPriority
                                 :mapRule mapRule :mapAdvice mapAdvice
                                 :mapTarget mapTarget :correlationId correlationId
                                 :fields (or more [])))))
(defn- reify-attribute-value-refset-item [item]
  (let [[valueId & more] (:fields item)]
    (map->AttributeValueRefsetItem (assoc item :valueId valueId :fields (or more [])))))

(defn- reify-owl-expression-refset-item [item]
  (let [[owlExpression & more] (:fields item)]
    (map->OWLExpressionRefsetItem (assoc item :owlExpression owlExpression :fields (or more [])))))

(defn- reify-module-dependency-refset-item [item]
  (let [[sourceEffectiveTime targetEffectiveTime & more] (:fields item)]
    (map->ModuleDependencyRefsetItem (assoc item :sourceEffectiveTime sourceEffectiveTime
                                                 :targetEffectiveTime targetEffectiveTime
                                                 :fields (or more [])))))

(defn refset-reifier
  "Given a sequence of attribute description concept identifiers, return a
  function to reify a SimpleRefset into its concrete subtype.

  The canonical pattern of attribute description concept identifiers is provided
  by SNOMED CT. For example, those for complex maps are found [here](https://confluence.ihtsdotools.org/display/DOCRELFMT/5.2.3.3+Complex+and+Extended+Map+from+SNOMED+CT+Reference+Sets)

  Reification of refset items could occur at time of import, or at runtime."
  [attribute-description-concept-ids]
  (match/match [attribute-description-concept-ids]
    [[449608002 900000000000533001 & _]] reify-association-refset-item
    [[449608002 900000000000511003 & _]] reify-language-refset-item
    [[900000000000500006 900000000000505001 & _]] reify-simple-map-refset-item
    [[900000000000500006 900000000000501005 900000000000502003 900000000000503008 900000000000504002 900000000000505001
      1193546000 609330002 & _]] reify-extended-map-refset-item
    [[900000000000500006 900000000000501005 900000000000502003 900000000000503008 900000000000504002 900000000000505001
      1193546000 & _]] reify-complex-map-refset-item
    [[449608002 900000000000491004 & _]] reify-attribute-value-refset-item ;; AttributeValueRefsetItem
    [[449608002 762677007 & _]] reify-owl-expression-refset-item
    [[900000000000535008 900000000000536009 900000000000537000 & _]] reify-module-dependency-refset-item
    :else identity))

(s/def ::refset-filename-pattern
  (s/with-gen (s/and string? #(every? #{\c \i \s} %))
              #(gen/fmap (fn [n] (apply str (repeatedly n (fn [] (rand-nth [\c \i \s])))))
                         (s/gen (s/int-in 1 10)))))
(s/fdef parse-fields
  :args (s/cat :pattern ::refset-filename-pattern
               :values (s/coll-of string?)))

(defn parse-fields
  "Parse the values 'v' using the pattern specification 'pattern'.
  Parameters:
  - pattern : a string containing characters c, i or s.
  - values  : a sequence of values to be parsed.

  Pattern definition
  - c : A SNOMED CT component identifier (SCTID) referring to a concept, description or relationship.
  - i : A signed integer
  - s : A UTF-8 text string.
  See https://confluence.ihtsdotools.org/display/DOCRELFMT/3.3.2+Release+File+Naming+Convention"
  [pattern values]
  (when-not (= (count pattern) (count values))
    (log/debug "length of pattern values not equal" {:pattern pattern :values values})
    (throw (ex-info "length of pattern values not equal" {:pattern pattern :values values})))
  (mapv (fn [[k v]]
          (case k \c (Long/parseLong v)
                  \i (Long/parseLong v)
                  \s v
                  (throw (ex-info "invalid refset pattern" {:pattern pattern :values values}))))
        (mapv vector (seq pattern) values)))

(defn parse-simple-refset-item [pattern v]
  (->SimpleRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component Id
    (parse-fields pattern (subvec v 6))))                   ;; slurp remaining fields as defined by rest of pattern

(defmethod ->vec SimpleRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)]
        (:fields r)))

(defn parse-association-refset-item [pattern v]
  (->AssociationRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component Id
    (Long/parseLong (v 6))                                  ;; target component Id))
    (parse-fields (subs pattern 1) (subvec v 7))))          ;; slurp remaining fields as defined by rest of pattern

(defmethod ->vec AssociationRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
         (:targetComponentId r)]
        (:fields r)))

(defn parse-language-refset-item [pattern v]
  (->LanguageRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component id
    (Long/parseLong (v 6))                                  ;; acceptability id
    (parse-fields (subs pattern 1) (subvec v 7))))          ;; slurp remaining fields as defined by rest of pattern

(defmethod ->vec LanguageRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
         (:acceptabilityId r)]
        (:fields r)))

(defn parse-refset-descriptor-item [_pattern v]
  (->RefsetDescriptorRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component id
    (Long/parseLong (v 6))                                  ;; attribute description id (a descendant of 900000000000457003)
    (Long/parseLong (v 7))                                  ;; attribute type id (a descendant of 900000000000459000)
    (Integer/parseInt (v 8))))                              ;; attribute order

(defmethod ->vec RefsetDescriptorRefsetItem [r]
  [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
   (:attributeDescriptionId r) (:attributeTypeId r) (:attributeOrder r)])

(defn parse-simple-map-refset-item [pattern v]
  (->SimpleMapRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; component id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component id
    (v 6)                                                   ;; map target
    (parse-fields (subs pattern 1) (subvec v 7))))          ;; slurp remaining fields as defined by rest of pattern

(defmethod ->vec SimpleMapRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
         (:mapTarget r)]
        (:fields r)))

(defn parse-complex-map-refset-item [pattern v]
  (->ComplexMapRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; component id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component id
    (Long/parseLong (v 6))                                  ;; map group
    (Long/parseLong (v 7))                                  ;; map priority
    (v 8)                                                   ;; map rule
    (v 9)                                                   ;; map advice
    (v 10)                                                  ;; map target
    (Long/parseLong (v 11))                                 ;; correlation
    (parse-fields (subs pattern 6) (subvec v 12))))         ;; slurp remaining fields as defined by rest of pattern

(defmethod ->vec ComplexMapRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
         (:mapGroup r) (:mapPriority r) (:mapRule r) (:mapAdvice r) (:mapTarget r) (:correlationId r)]
        (:fields r)))

(defn parse-extended-map-refset-item [pattern v]
  (->ExtendedMapRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; component id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component id
    (Long/parseLong (v 6))                                  ;; map group
    (Long/parseLong (v 7))                                  ;; map priority
    (v 8)                                                   ;; map rule
    (v 9)                                                   ;; map advice
    (v 10)                                                  ;; map target
    (Long/parseLong (v 11))                                 ;; correlation
    (Long/parseLong (v 12))                                 ;; map category id
    (parse-fields (subs pattern 7) (subvec v 13))))         ;; slurp all fields into a vector defined by pattern

(defmethod ->vec ExtendedMapRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
         (:mapGroup r) (:mapPriority r) (:mapRule r) (:mapAdvice r) (:mapTarget r) (:correlationId r) (:mapCategoryId r)]
        (:fields r)))

(defn parse-attribute-value-refset-item [pattern v]
  (->AttributeValueRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; component id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component id
    (Long/parseLong (v 6))
    (parse-fields (subs pattern 1) (subvec v 7))))          ;; slurp all fields into a vector defined by pattern

(defmethod ->vec AttributeValueRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
         (:valueId r)]
        (:fields r)))

(defn parse-owl-expression-refset-item [pattern v]
  (->OWLExpressionRefsetItem
    (unsafe-parse-uuid (v 0))                               ;; component id
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component id
    (v 6)                                                   ;; OWL expression
    (parse-fields (subs pattern 1) (subvec v 7))))          ;; slurp all fields into a vector defined by pattern

(defmethod ->vec OWLExpressionRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
         (:owlExpression r)]
        (:fields r)))

(defn parse-module-dependency-refset-item [pattern v]
  (->ModuleDependencyRefsetItem
    (unsafe-parse-uuid (v 0))
    (parse-date (v 1))                                      ;; effective time
    (parse-bool (v 2))                                      ;; active?
    (Long/parseLong (v 3))                                  ;; module Id
    (Long/parseLong (v 4))                                  ;; refset Id
    (Long/parseLong (v 5))                                  ;; referenced component id
    (parse-date (v 6))
    (parse-date (v 7))
    (parse-fields (subs pattern 2) (subvec v 8))))          ;; standard pattern is 'ss', so use pattern string after *2* characters

(defmethod ->vec ModuleDependencyRefsetItem [r]
  (into [(:id r) (:effectiveTime r) (:active r) (:moduleId r) (:refsetId r) (:referencedComponentId r)
         (:sourceEffectiveTime r) (:targetEffectiveTime r)]
        (:fields r)))

(def parsers
  {:info.snomed/Concept                parse-concept
   :info.snomed/Description            parse-description
   :info.snomed/Relationship           parse-relationship

   ;; types of reference set
   :info.snomed/RefsetDescriptorRefset parse-refset-descriptor-item
   :info.snomed/SimpleRefset           parse-simple-refset-item
   :info.snomed/AssociationRefset      parse-association-refset-item
   :info.snomed/LanguageRefset         parse-language-refset-item
   :info.snomed/SimpleMapRefset        parse-simple-map-refset-item
   :info.snomed/ComplexMapRefset       parse-complex-map-refset-item
   :info.snomed/ExtendedMapRefset      parse-extended-map-refset-item
   :info.snomed/AttributeValueRefset   parse-attribute-value-refset-item
   ;:info.snomed/OWLExpressionRefset    parse-owl-expression-refset-item})
   :info.snomed/ModuleDependencyRefset parse-module-dependency-refset-item})

(s/def ::type parsers)
(s/def ::data seq)
(s/def ::batch (s/keys :req-un [::type ::data]))

(defn parse-batch
  "Lazily parse a batch of SNOMED entities, returning a batch with
  data as parsed entities and not simply raw imported data."
  [batch]
  (when-not (s/valid? ::batch batch)
    (throw (ex-info "invalid batch:" (s/explain-data ::batch batch))))
  (if-let [parser (or (:parser batch) (get parsers (:type batch)))]
    (assoc batch :data (doall (map parser (:data batch))))
    (throw (ex-info "no parser for batch type" (:type batch)))))

(derive :info.snomed/Concept :info.snomed/Component)
(derive :info.snomed/Description :info.snomed/Component)
(derive :info.snomed/Relationship :info.snomed/Component)
(derive :info.snomed/Refset :info.snomed/Component)
(derive :info.snomed/RefsetDescriptorRefset :info.snomed/Refset)
(derive :info.snomed/SimpleRefset :info.snomed/Refset)
(derive :info.snomed/ExtendedRefset :info.snomed/Refset)
(derive :info.snomed/AssociationRefset :info.snomed/Refset)
(derive :info.snomed/LanguageRefset :info.snomed/Refset)
(derive :info.snomed/MapRefset :info.snomed/Refset)
(derive :info.snomed/SimpleMapRefset :info.snomed/MapRefset)
(derive :info.snomed/ComplexMapRefset :info.snomed/MapRefset)
(derive :info.snomed/ExtendedMapRefset :info.snomed/ComplexMapRefset)
(derive :info.snomed/AttributeValueRefset :info.snomed/Refset)
(derive :info.snomed/OWLExpressionRefset :info.snomed/Refset)
(derive :info.snomed/ModuleDependencyRefset :info.snomed/Refset)

(derive Concept :info.snomed/Concept)
(derive Description :info.snomed/Description)
(derive Relationship :info.snomed/Relationship)
(derive RefsetDescriptorRefsetItem :info.snomed/RefsetDescriptorRefset)
(derive SimpleRefsetItem :info.snomed/SimpleRefset)
(derive AssociationRefsetItem :info.snomed/AssociationRefset)
(derive LanguageRefsetItem :info.snomed/LanguageRefset)
(derive SimpleMapRefsetItem :info.snomed/SimpleMapRefset)
(derive ComplexMapRefsetItem :info.snomed/ComplexMapRefset)
(derive ExtendedMapRefsetItem :info.snomed/ExtendedMapRefset)
(derive AttributeValueRefsetItem :info.snomed/AttributeValueRefset)
(derive OWLExpressionRefsetItem :info.snomed/OWLExpressionRefset)
(derive ModuleDependencyRefsetItem :info.snomed/ModuleDependencyRefset)

(defrecord Result
  [^long id
   ^long conceptId
   ^String term
   ^String preferredTerm]
  IResult
  (id [_] id)
  (conceptId [_] conceptId)
  (term [_] term)
  (preferredTerm [_] preferredTerm))

(def snomed-file-pattern
  #"(?x) # allow white-space and comments
  # file-type
  (?<filetype>(?<status>[x|z]*)(?<type>sct|der|doc|res|tls)(?<format>.*?))
  _
  # content-type
  (?<contenttype>((?<pattern>.*?)(?<entity>Concept|Relationship|Refset|Description|TextDefinition|StatedRelationship|Identifier))|(.*?))
  _
  # content-sub-type - this element is optional entirely, so include a trailing underscore
  ((?<contentsubtype>
    (?<summary>
      (?<refsettype>SimpleMap|Simple|Ordered|AttributeValue|Language|Association|OrderedAssociation|Annotation|QuerySpecification|
      ComplexMap|ExtendedMap|RefsetDescriptor|ModuleDependency|DescriptionType|MRCMDomain|MRCMAttributeDomain|
      MRCMAttributeRange|MRCMModuleScope|OWLExpression)?
      (?<summaryextra>.*?)?)?
    (?<releasetype>Full|Snapshot|Delta)(?<docstatus>Current|Draft|Review)?(-(?<languagecode>.*?))?)
  _)?
  # country-namespace
  (?<countrynamespace>(?<countrycode>.*?)(?<namespace>\d*))
  _
  # version-date
  (?<versiondate>\d{8})
  \.
  # file extension
  (?<fileextension>.*?)
  $")


(defn parse-snomed-filename
  "Parse a filename according the specifications outlined in
   https://confluence.ihtsdotools.org/display/DOCRELFMT/3.3.2+Release+File+Naming+Convention
   Each filename should match the following pattern:
   [FileType] _ [ContentType] _ [ContentSubType] _ [CountryNamespace] _ [VersionDate] . [FileExtension] .
   Returns a map containing all the information from the filename, or nil
   if the filename does not match the specification."
  [^String filename]
  (let [nm (.getName (File. filename))
        m (re-matcher snomed-file-pattern nm)]
    (when (.matches m)
      (let [entity (.group m "entity")
            pattern (.group m "pattern")
            refset-type (or (.group m "refsettype") (when (= "Refset" entity) "Simple"))
            component-name (str refset-type entity)
            identifier (when-not (str/blank? component-name) (keyword "info.snomed" component-name))]
        {:path              filename
         :filename          nm
         :component         component-name
         :identifier        identifier
         :parser            (when-let [p (get parsers identifier)]
                              (if (= "Refset" entity) (fn [row] (p pattern row)) p))
         :file-type         (.group m "filetype")
         :status            (.group m "status")
         :type              (.group m "type")
         :format            (.group m "format")
         :content-type      (.group m "contenttype")
         :pattern           pattern
         :entity            entity
         :content-subtype   (.group m "contentsubtype")
         :summary           (.group m "summary")
         :refset-type       refset-type
         :summary-extra     (.group m "summaryextra")
         :release-type      (.group m "releasetype")
         :doc-status        (.group m "docstatus")
         :language-code     (.group m "languagecode")
         :country-namespace (.group m "countrynamespace")
         :country-code      (.group m "countrycode")
         :namespace-id      (.group m "namespace")
         :version-date      (parse-date (.group m "versiondate"))
         :file-extension    (.group m "fileextension")}))))

(defn partition-identifier
  "Return the partition from the identifier.
  The partition identifier is stored in the penultimate last two digits.
  See https://confluence.ihtsdotools.org/display/DOCRELFMT/5.5.+Partition+Identifier
  identifier: 0123456789
  meaning:    xxxxxxxppc
  pp - partition identifier
  c  - check digit."
  [id]
  (let [s (str id), l (.length s)]
    (when (> l 2) (.substring s (- l 3) (- l 1)))))

(def partitions
  "Map of partition identifiers to type of entity.
  The penultimate two digits of a SNOMED CT identifier given the partition identifier."
  {"00" :info.snomed/Concept
   "10" :info.snomed/Concept
   "01" :info.snomed/Description
   "11" :info.snomed/Description
   "02" :info.snomed/Relationship
   "12" :info.snomed/Relationship})

(defn identifier->type
  "Get the type of SNOMED CT entity from the identifier specified.

  The types are represented as namespaced keywords:
  - :info.snomed/Concept
  - :info.snomed/Description
  - :info.snomed/Relationship."
  [id]
  (get partitions (partition-identifier id)))

(defn identifier->namespace
  "Return a string representing the namespace of a long-form identifier, or nil.
  See [[https://confluence.ihtsdotools.org/display/DOCRELFMT/6.6+Namespace-Identifier]]
  The namespace-identifier is a string representation of an integer value, left
  padded with 0s as necessary to ensure there are always seven digits in the
  value."
  [id]
  (let [s (str id), l (.length s)]
    ;; a 'long' identifier will have 7 digit namespace, 2 char partition id and a check digit (ie 10 chars or more)
    (when (and (> l 9) (= \1 (.charAt s (- l 3))))
      (.substring s (- l 10) (- l 3)))))

(def Root 138875005)

;; Metadata concepts
(def Primitive 900000000000074008)                          ;; Not sufficiently defined by necessary conditions definition status (core metadata concept
(def Defined 900000000000073002)                            ;; Sufficiently defined by necessary conditions definition status (core metadata concept)
(def FullySpecifiedName 900000000000003001)
(def Synonym 900000000000013009)
(def Definition 900000000000550004)
(def Preferred 900000000000548007)
(def Acceptable 900000000000549004)
(def OnlyInitialCharacterCaseInsensitive 900000000000020002)
(def EntireTermCaseSensitive 900000000000017005)
(def EntireTermCaseInsensitive 900000000000448009)

;; Top level concepts
(def BodyStructure 123037004)
(def ClinicalFinding 404684003)
(def EnvironmentGeographicLocation 308916002)
(def Event 308916002)
(def ObservableEntity 363787002)
(def Organism 410607006)
(def PharmaceuticalBiologicalProduct 373873005)
(def PhysicalForce 78621006)
(def PhysicalObject 260787004)
(def Procedure 71388002)
(def QualifierValue 362981000)
(def RecordArtefact 419891008)
(def SituationWithExplicitContext 243796009)
(def SocialContext 243796009)
(def Specimen 123038009)
(def StagingAndScales 254291000)
(def Substance 254291000)
(def LinkageConcept 106237007)

;; Special concepts
(def SpecialConcept 370115009)
(def NavigationalConcept 363743006)
(def ReferenceSetConcept 900000000000455006)

;; Relationship type concepts
(def Attribute 246061005)
(def ConceptModelAttribute 410662002)
(def Access 260507000)
(def AssociatedFinding 246090004)
(def AssociatedMorphology 116676008)
(def AssociatedProcedure 363589002)
(def AssociatedWith 47429007)
(def After 255234002)
(def CausativeAgent 246075003)
(def DueTo 42752001)
(def ClinicalCourse 263502005)
(def Component 246093002)
(def DirectSubstance 363701004)
(def Episodicity 246456000)
(def FindingContext 408729009)
(def FindingInformer 419066007)
(def FindingMethod 418775008)
(def FindingSite 363698007)
(def HasActiveIngredient 127489000)
(def HasDefinitionalManifestation 363705008)
(def HasDoseForm 411116001)
(def HasFocus 363702006)
(def HasIntent 363703001)
(def HasInterpretation 363713009)
(def HasSpecimen 116686009)
(def Interprets 363714003)
(def IsA 116680003)
(def Laterality 272741003)
(def MeasurementMethod 370129005)
(def Method 260686004)
(def Occurrence 246454002)
(def PartOf 123005000)
(def PathologicalProcess 370135005)
(def Priority 260870009)
(def ProcedureContext 408730004)
(def ProcedureDevice 405815000)
(def DirectDevice 363699004)
(def IndirectDevice 363710007)
(def UsingDevice 424226004)
(def UsingAccessDevice 425391005)
(def ProcedureMorphology 405816004)
(def DirectMorphology 363700003)
(def IndirectMorphology 363709002)
(def ProcedureSite 363704007)
(def ProcedureSiteDirect 405813007)
(def ProcedureSiteIndirect 405814001)
(def Property 370130000)
(def RecipientCategory 370131001)
(def RevisionStatus 246513007)
(def RouteOfAdministration 410675002)
(def ScaleType 370132008)
(def Severity 246112005)
(def SpecimenProcedure 118171006)
(def SpecimenSourceIdentity 118170007)
(def SpecimenSourceMorphology 118168003)
(def SpecimenSourceTopography 118169006)
(def SpecimenSubstance 370133003)
(def SubjectOfInformation 131195008)
(def SubjectRelationshipContext 408732007)
(def SurgicalApproach 424876005)
(def TemporalContext 408731000)
(def TimeAspect 370134009)
(def UsingEnergy 424244007)
(def UsingSubstance 42436100)

;; Other common concepts
(def Side 182353008)
(def LateralisableReferenceSet 723264001)

;; Historical associations - for inactive concepts
(def HistoricalAssociationReferenceSet 900000000000522004)  ;; parent of all historical association reference sets
(def PossiblyEquivalentToReferenceSet 900000000000523009)
(def ReplacedByReferenceSet 900000000000526001)
(def SimilarToReferenceSet 900000000000529008)
(def SameAsReferenceSet 900000000000527005)
(def WasAReferenceSet 900000000000528000)
(def PartiallyEquivalentToReferenceSet 1186924009)
(def MovedToReferenceSet 900000000000524003)                ;; |MOVED TO association reference set|
(def MovedFromReferenceSet 900000000000525002)              ;; |MOVED FROM association reference set|
(def AlternativeReferenceSet 900000000000530003)            ;; |ALTERNATIVE association reference set|
(def RefersToReferenceSet 900000000000531004)               ;; |REFERS TO concept association reference set|

;; SNOMED CT 'core' module
(def CoreModule 900000000000207008)
;; SNOMED CT 'model' component and reference set
(def ModelModule 900000000000012004)
(def ModuleDependencyReferenceSet 900000000000534007)

(defn is-primitive?
  "Is this concept primitive? ie not sufficiently defined by necessary conditions?"
  [^Concept c]
  (= Primitive (:definitionStatusId c)))

(defn is-defined?
  "Is this concept fully defined? ie sufficiently defined by necessary conditions?"
  [^Concept c]
  (= Defined (:definitionStatusId c)))

(defn is-fully-specified-name?
  [^Description d]
  (= FullySpecifiedName (:typeId d)))

(defn is-synonym?
  [^Description d]
  (= Synonym (:typeId d)))

(s/fdef term->lowercase
  :args (s/cat :description :info.snomed/Description))
(defn term->lowercase
  "Return the term of the description as a lower-case string,
  if possible, as determined by the case significance flag."
  [^Description d]
  (case (:caseSignificanceId d)
    ;; initial character is case-insensitive - we can make initial character lowercase
    900000000000020002
    (when (> (count (:term d)) 0)
      (str (str/lower-case (first (:term d)))
           (subs (:term d) 1)))
    ;; entire term case-insensitive - just make it all lower-case
    900000000000448009
    (str/lower-case (:term d))
    ;; entire term is case-sensitive - can't do anything
    900000000000017005
    (:term d)
    ;; fallback option - don't do anything
    (:term d)))

;; just an experiment with multimethods...
(defmulti valid? #(identifier->type (:id %)))
(defmethod valid? :info.snomed/Concept [m]
  (and
    (verhoeff/valid? (:id m))
    (not (nil? (:effectiveDate m)))))
(defmethod valid? :default [_] false)

(comment
  (identifier->type 24700007)
  (identifier->type 24700030)
  (verhoeff/valid? 24700002)

  (valid? {:wibble "Hi there" :flibble "Flibble"})

  (isa? (class (->Concept 24700007 (LocalDate/now) true 0 0)) :info.snomed/Concept)

  (parse-batch {:type :info.snomed/Concept :data [["24700007" "20200101" "true" "0" "0"]]}))
