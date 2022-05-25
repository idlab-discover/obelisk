package idlab.obelisk.services.pub.ngsi.helpers

val batchRawJson = """
        [
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": [
              "Sensor",
              "Device"
            ],
            "@id": "urn:ngsi-ld:device:12227"
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@id": "urn:ngsi-ld:spatialSamplingFeature:12227",
            "@type": "SpatialSamplingFeature",
            "SamplingFeature.sampledFeature": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "SpatialSamplingFeature.shape": {
              "@type": "Relationship",
              "object": {
                "http://www.opengis.net/ont/geosparql#asWKT": {
                  "value": "POINT (3.878704062191679 50.97737071517522)",
                  "type": "Property"
                }
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_0",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:31:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:31:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_1",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:32:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:32:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_2",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:33:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:33:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_3",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:34:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:34:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_4",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:35:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:35:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_5",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:36:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:36:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_6",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:37:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:37:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_7",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:38:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:38:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_8",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:39:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:39:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_9",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:40:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:40:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_10",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:41:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:41:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_11",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:42:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:42:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_12",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:43:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:43:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_13",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:44:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:44:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_14",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:45:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:45:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_15",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:46:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:46:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_16",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:47:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:47:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_17",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:48:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:48:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_18",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:49:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:49:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_19",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:50:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:50:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_20",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:51:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:51:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_21",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:52:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:52:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_22",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:53:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:53:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_23",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:54:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:54:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_24",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:55:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:55:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_25",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:56:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:56:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_26",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:57:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:57:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_27",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:58:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:58:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_28",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:59:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:59:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_29",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:00:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:00:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_30",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:01:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:01:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_31",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:02:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:02:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_32",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:03:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:03:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_33",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:04:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:04:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_34",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:05:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:05:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_35",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:06:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:06:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_36",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:07:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:07:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_37",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:08:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:08:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_38",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:09:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:09:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_39",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:10:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:10:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_40",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:11:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:11:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_41",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:12:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:12:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_42",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:13:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:13:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_43",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:14:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:14:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_44",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:15:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:15:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_45",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:16:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:16:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_46",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:17:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:17:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_47",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:18:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:18:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_48",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:19:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:19:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_49",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:20:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:20:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_50",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:21:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:21:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_51",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:22:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:22:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_52",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:23:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:23:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_53",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:24:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:24:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608000:60960042_54",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:25:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Massemen_P",
                "description": "P04_020"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:25:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": [
              "Sensor",
              "Device"
            ],
            "@id": "urn:ngsi-ld:device:12235"
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@id": "urn:ngsi-ld:spatialSamplingFeature:12235",
            "@type": "SpatialSamplingFeature",
            "SamplingFeature.sampledFeature": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "SpatialSamplingFeature.shape": {
              "@type": "Relationship",
              "object": {
                "http://www.opengis.net/ont/geosparql#asWKT": {
                  "value": "POINT (3.898286316286323 50.74259588707348)",
                  "type": "Property"
                }
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_0",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:31:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:31:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_1",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:32:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:32:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_2",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:33:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:33:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_3",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:34:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:34:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_4",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:35:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:35:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_5",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:36:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:36:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_6",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:37:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:37:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_7",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:38:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:38:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_8",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:39:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:39:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_9",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:40:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:40:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_10",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:41:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:41:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_11",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:42:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:42:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_12",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:43:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:43:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_13",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:44:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:44:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_14",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:45:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:45:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_15",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:46:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:46:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_16",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:47:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:47:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_17",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:48:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:48:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_18",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:49:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:49:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_19",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:50:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:50:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_20",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:51:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:51:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_21",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:52:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:52:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_22",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:53:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:53:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_23",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:54:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:54:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_24",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:55:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:55:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_25",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:56:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:56:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_26",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:57:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:57:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_27",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:58:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:58:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_28",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T09:59:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T09:59:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_29",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:00:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:00:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_30",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:01:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:01:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_31",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:02:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:02:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_32",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:03:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:03:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_33",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:04:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:04:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_34",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:05:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:05:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_35",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:06:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:06:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_36",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:07:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:07:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_37",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:08:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:08:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_38",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:09:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:09:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_39",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:10:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:10:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_40",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:11:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:11:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_41",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:12:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:12:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_42",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:13:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:13:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_43",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:14:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:14:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_44",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:15:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:15:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_45",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:16:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:16:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_46",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:17:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:17:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_47",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:18:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:18:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_48",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:19:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:19:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_49",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:20:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:20:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_50",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:21:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:21:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_51",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:22:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:22:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_52",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:23:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:23:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_53",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:24:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:24:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@type": "Observation",
            "@id": "urn:ngsi-ld:observation:1645608300:60992042_54",
            "Observation.observedProperty": {
              "@type": "Relationship",
              "object": "http://purl.obolibrary.org/obo/CHEBI_28112"
            },
            "Observation.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:25:00.000+01:00"
            },
            "Observation.metadata": {
              "@type": "Relationship",
              "object": {
                "@type": "https://purl.eu/doc/applicationprofile/AirAndWater/Core/#Metadata",
                "title": "Moerbeke_P",
                "description": "P07_021"
              }
            },
            "Observation.result": {
              "@type": "Relationship",
              "object": {
                "@type": "https://schema.org/QuantitativeValue",
                "unitText": "millimeter per hour",
                "unitCode": "mm/h",
                "value": 0,
                "observedAt": "2022-02-23T10:25:00.000+01:00"
              }
            }
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@id": "urn:ngsi-ld:observationCollection:1645608000:60960042",
            "@type": "ObservationCollection",
            "ObservationCollection.madeBySensor": {
              "@type": "Relationship",
              "object": "urn:ngsi-ld:device:12227"
            },
            "ObservationCollection.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:20:00.000+01:00"
            },
            "observedAt": {
              "@type": "Property",
              "value": "2022-02-23T10:20:00.000+01:00"
            },
            "ObservationCollection.hasFeatureOfInterest": {
              "@type": "Relationship",
              "object": "urn:ngsi-ld:spatialSamplingFeature:12227"
            },
            "ObservationCollection.hasMember": [
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_0"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_1"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_2"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_3"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_4"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_5"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_6"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_7"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_8"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_9"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_10"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_11"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_12"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_13"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_14"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_15"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_16"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_17"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_18"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_19"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_20"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_21"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_22"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_23"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_24"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_25"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_26"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_27"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_28"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_29"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_30"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_31"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_32"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_33"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_34"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_35"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_36"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_37"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_38"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_39"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_40"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_41"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_42"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_43"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_44"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_45"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_46"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_47"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_48"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_49"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_50"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_51"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_52"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_53"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608000:60960042_54"
              }
            ]
          },
          {
            "@context": [
              "https://uri.etsi.org/ngsi-ld/v1/ngsi-ld-core-context.jsonld",
              "https://raw.githubusercontent.com/Informatievlaanderen/purl-eu-generated/main/doc/applicationprofile/AirAndWater/Core/kandidaatstandaard/2021-10-01/context/OSLO-airAndWater-Core-ap_en.jsonld"
            ],
            "@id": "urn:ngsi-ld:observationCollection:1645608300:60992042",
            "@type": "ObservationCollection",
            "ObservationCollection.madeBySensor": {
              "@type": "Relationship",
              "object": "urn:ngsi-ld:device:12235"
            },
            "ObservationCollection.resultTime": {
              "@type": "Property",
              "value": "2022-02-23T10:25:00.000+01:00"
            },
            "observedAt": {
              "@type": "Property",
              "value": "2022-02-23T10:25:00.000+01:00"
            },
            "ObservationCollection.hasFeatureOfInterest": {
              "@type": "Relationship",
              "object": "urn:ngsi-ld:spatialSamplingFeature:12235"
            },
            "ObservationCollection.hasMember": [
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_0"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_1"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_2"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_3"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_4"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_5"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_6"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_7"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_8"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_9"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_10"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_11"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_12"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_13"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_14"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_15"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_16"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_17"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_18"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_19"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_20"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_21"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_22"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_23"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_24"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_25"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_26"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_27"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_28"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_29"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_30"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_31"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_32"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_33"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_34"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_35"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_36"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_37"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_38"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_39"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_40"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_41"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_42"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_43"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_44"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_45"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_46"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_47"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_48"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_49"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_50"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_51"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_52"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_53"
              },
              {
                "@type": "Relationship",
                "object": "urn:ngsi-ld:observation:1645608300:60992042_54"
              }
            ]
          }
        ]
    """.trimIndent()
