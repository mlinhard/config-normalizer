/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.qa.jdg.config;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.factories.ComponentRegistry;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.remoting.transport.Transport;
import org.infinispan.remoting.transport.jgroups.JGroupsTransport;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;
import org.jgroups.JChannel;

/**
 * 
 * Exposes the normalized config properties via JMX.
 * 
 * @author Michal Linhard (mlinhard@redhat.com)
 */
public class ConfigNormalizerService {
   private static final Log log = LogFactory.getLog(ConfigNormalizerService.class);

   public static final ConfigNormalizerService INSTANCE = new ConfigNormalizerService();

   public interface CacheManagerDataMBean {
      /**
       * 
       * @return all configuration properties
       */
      Properties getNormalizedConfig();

      /**
       * 
       * @return the global configuration properties
       */
      Properties getNormalizedConfigGlobal();

      /**
       * 
       * @return the configuration properties for JGroups channel
       */
      Properties getNormalizedConfigJGroups();

      /**
       * 
       * @param cacheName
       * @return Config properties for specific cache
       */
      Properties getNormalizedConfigCache(String cacheName);

      /**
       * 
       * Saves the properties of this cache manager to a standard properties file.
       * 
       * @param file
       */
      void saveSortedProperties(String file);

      /**
       * 
       * Saves the properties of this cache manager to a XML file.
       * 
       * @param file
       */
      void saveSortedXML(String file);

      /**
       * 
       * Saves the global config.
       * 
       * @param file
       */
      void saveSortedPropertiesGlobalConfiguration(String file);

      /**
       * 
       * Saves the global config.
       * 
       * @param file
       */
      void saveSortedXMLGlobalConfiguration(String file);

      /**
       * 
       * Saves the jgroups config.
       * 
       * @param file
       */
      void saveSortedPropertiesJGroups(String file);

      /**
       * 
       * Saves the jgroups config.
       * 
       * @param file
       */
      void saveSortedXMLJGroups(String file);

      /**
       * 
       * Saves the cache config.
       * 
       * @param file
       */
      void saveSortedPropertiesCache(String file, String cacheName);

      /**
       * 
       * Saves the cache config.
       * 
       * @param file
       */
      void saveSortedXMLCache(String file, String cacheName);
   }

   private static class CacheManagerData implements CacheManagerDataMBean {
      private String cacheManagerName;
      private GlobalComponentRegistry globalComponentRegistry;
      private GlobalConfiguration globalConfiguration;
      private Map<String, Configuration> configByCacheName = new HashMap<String, Configuration>();
      private JChannel jgroupsChannel;

      public CacheManagerData(GlobalComponentRegistry globalComponentRegistry, GlobalConfiguration globalConfiguration) {
         this.globalComponentRegistry = globalComponentRegistry;
         this.globalConfiguration = globalConfiguration;
         this.cacheManagerName = getCacheManagerName(globalComponentRegistry, globalConfiguration);
         MBeanServer server = ManagementFactory.getPlatformMBeanServer();
         try {
            server.registerMBean(this, new ObjectName("jboss.infinispan:type=CacheManager,name=\"" + cacheManagerName + "\",component=ConfigNormalizer"));
         } catch (Exception e) {
            log.error("Couldn't register MBean for cache manager " + cacheManagerName, e);
         }
      }

      @Override
      public Properties getNormalizedConfig() {
         try {
            if (jgroupsChannel == null) {
               jgroupsChannel = getChannel(globalComponentRegistry);
            }

            return ConfigNormalizer.reflectProperties(globalConfiguration, configByCacheName, jgroupsChannel);
         } catch (Exception e) {
            log.error("Error while reflecting properties for manager: " + cacheManagerName, e);
            return new Properties();
         }
      }

      public void addCacheConfiguration(String cacheName, Configuration configuration) {
         configByCacheName.put(cacheName, configuration);
      }

      private JChannel getChannel(GlobalComponentRegistry globalComponentRegistry) {
         String managerName = getCacheManagerName(globalComponentRegistry, globalComponentRegistry.getGlobalConfiguration());
         try {
            Transport transport = globalComponentRegistry.getComponent(Transport.class);
            if (transport == null || !(transport instanceof JGroupsTransport)) {
               log.warn("Can't extract JGroups channel from manager " + managerName);
               return null;
            } else {
               JGroupsTransport jGroupsTransport = (JGroupsTransport) transport;
               return (JChannel) jGroupsTransport.getChannel();
            }
         } catch (Exception e) {
            log.error("Error while obtaining JGroupsTransport from manager " + managerName, e);
            return null;
         }
      }

      @Override
      public void saveSortedProperties(String file) {
         try {
            ConfigNormalizer.storeSortedProperties(getNormalizedConfig(), file);
         } catch (Exception e) {
            log.error("Error saving config properties of chache manager " + cacheManagerName + " to file " + file, e);
         }
      }

      @Override
      public void saveSortedXML(String file) {
         try {
            ConfigNormalizer.storeSortedPropertiesAsXML(getNormalizedConfig(), file);
         } catch (Exception e) {
            log.error("Error saving config properties of chache manager " + cacheManagerName + " to file " + file, e);
         }
      }

      @Override
      public Properties getNormalizedConfigGlobal() {
         try {
            return ConfigNormalizer.reflectProperties(globalConfiguration, "");
         } catch (Exception e) {
            log.error("Error while reflecting properties for manager: " + cacheManagerName, e);
            return new Properties();
         }
      }

      @Override
      public Properties getNormalizedConfigJGroups() {
         try {
            if (jgroupsChannel == null) {
               jgroupsChannel = getChannel(globalComponentRegistry);
            }
            if (jgroupsChannel == null) {
               log.error("Error while reflecting properties for manager: " + cacheManagerName + ": JGroups channel not available.");
               return new Properties();
            }
            return ConfigNormalizer.reflectProperties(jgroupsChannel, "");
         } catch (Exception e) {
            log.error("Error while reflecting properties for manager: " + cacheManagerName, e);
            return new Properties();
         }
      }

      @Override
      public Properties getNormalizedConfigCache(String cacheName) {
         try {
            Configuration config = configByCacheName.get(cacheName);
            if (config == null) {
               log.error("Error while reflecting properties for manager: " + cacheManagerName + ": config not found.");
               return new Properties();
            }
            return ConfigNormalizer.reflectProperties(config, "");
         } catch (Exception e) {
            log.error("Error while reflecting properties for manager: " + cacheManagerName, e);
            return new Properties();
         }
      }

      @Override
      public void saveSortedPropertiesGlobalConfiguration(String file) {
         try {
            ConfigNormalizer.storeSortedProperties(getNormalizedConfigGlobal(), file);
         } catch (Exception e) {
            log.error("Error saving config properties of chache manager " + cacheManagerName + " to file " + file, e);
         }
      }

      @Override
      public void saveSortedXMLGlobalConfiguration(String file) {
         try {
            ConfigNormalizer.storeSortedPropertiesAsXML(getNormalizedConfigGlobal(), file);
         } catch (Exception e) {
            log.error("Error saving config properties of chache manager " + cacheManagerName + " to file " + file, e);
         }
      }

      @Override
      public void saveSortedPropertiesJGroups(String file) {
         try {
            ConfigNormalizer.storeSortedProperties(getNormalizedConfigJGroups(), file);
         } catch (Exception e) {
            log.error("Error saving config properties of chache manager " + cacheManagerName + " to file " + file, e);
         }
      }

      @Override
      public void saveSortedXMLJGroups(String file) {
         try {
            ConfigNormalizer.storeSortedPropertiesAsXML(getNormalizedConfigJGroups(), file);
         } catch (Exception e) {
            log.error("Error saving config properties of chache manager " + cacheManagerName + " to file " + file, e);
         }
      }

      @Override
      public void saveSortedPropertiesCache(String file, String cacheName) {
         try {
            ConfigNormalizer.storeSortedProperties(getNormalizedConfigCache(cacheName), file);
         } catch (Exception e) {
            log.error("Error saving config properties of chache manager " + cacheManagerName + " to file " + file, e);
         }

      }

      @Override
      public void saveSortedXMLCache(String file, String cacheName) {
         try {
            ConfigNormalizer.storeSortedPropertiesAsXML(getNormalizedConfigCache(cacheName), file);
         } catch (Exception e) {
            log.error("Error saving config properties of chache manager " + cacheManagerName + " to file " + file, e);
         }
      }
   }

   private Map<GlobalComponentRegistry, CacheManagerData> dataByGCR = new HashMap<GlobalComponentRegistry, ConfigNormalizerService.CacheManagerData>();

   public ConfigNormalizerService() {
      log.info("Starting ...");

   }

   private CacheManagerData createCacheManagerData(GlobalComponentRegistry globalComponentRegistry, GlobalConfiguration globalConfiguration) {
      CacheManagerData data = new CacheManagerData(globalComponentRegistry, globalConfiguration);

      return data;
   }

   public void registerCacheManager(GlobalComponentRegistry globalComponentRegistry, GlobalConfiguration globalConfiguration) {
      String managerName = getCacheManagerName(globalComponentRegistry, globalConfiguration);
      log.debug("Registering cache manager " + managerName + " ...");
      CacheManagerData data = dataByGCR.get(globalComponentRegistry);
      if (data != null) {
         log.error("Cache manager already registered: " + managerName);
      } else {
         dataByGCR.put(globalComponentRegistry, createCacheManagerData(globalComponentRegistry, globalConfiguration));
      }
   }

   private static String getCacheManagerName(GlobalComponentRegistry globalComponentRegistry, GlobalConfiguration globalConfiguration) {
      if (globalConfiguration != null && globalConfiguration.globalJmxStatistics() != null) {
         return globalConfiguration.globalJmxStatistics().cacheManagerName();
      } else {
         // compute a technical name from GCR
         return "GCR@" + Integer.toHexString(globalComponentRegistry.hashCode());
      }
   }

   public void registerCache(ComponentRegistry componentRegistry, Configuration configuration, String cacheName) {
      log.debug("Registering cache " + cacheName + " ...");
      CacheManagerData data = dataByGCR.get(componentRegistry.getGlobalComponentRegistry());
      if (data == null) {
         log.warn("Couldn't find cache manager for cache " + cacheName);
      } else {
         data.addCacheConfiguration(cacheName, configuration);
      }
   }

}
