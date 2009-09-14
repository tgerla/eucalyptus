/*******************************************************************************
 *Copyright (c) 2009 Eucalyptus Systems, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, only version 3 of the License.
 * 
 * 
 * This file is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Please contact Eucalyptus Systems, Inc., 130 Castilian
 * Dr., Goleta, CA 93101 USA or visit <http://www.eucalyptus.com/licenses/>
 * if you need additional information or have any questions.
 * 
 * This file may incorporate work covered under the following copyright and
 * permission notice:
 * 
 * Software License Agreement (BSD License)
 * 
 * Copyright (c) 2008, Regents of the University of California
 * All rights reserved.
 * 
 * Redistribution and use of this software in source and binary forms, with
 * or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. USERS OF
 * THIS SOFTWARE ACKNOWLEDGE THE POSSIBLE PRESENCE OF OTHER OPEN SOURCE
 * LICENSED MATERIAL, COPYRIGHTED MATERIAL OR PATENTED MATERIAL IN THIS
 * SOFTWARE, AND IF ANY SUCH MATERIAL IS DISCOVERED THE PARTY DISCOVERING
 * IT MAY INFORM DR. RICH WOLSKI AT THE UNIVERSITY OF CALIFORNIA, SANTA
 * BARBARA WHO WILL THEN ASCERTAIN THE MOST APPROPRIATE REMEDY, WHICH IN
 * THE REGENTS’ DISCRETION MAY INCLUDE, WITHOUT LIMITATION, REPLACEMENT
 * OF THE CODE SO IDENTIFIED, LICENSING OF THE CODE SO IDENTIFIED, OR
 * WITHDRAWAL OF THE CODE CAPABILITY TO THE EXTENT NEEDED TO COMPLY WITH
 * ANY SUCH LICENSES OR RIGHTS.
 *******************************************************************************/
/*
 */

/*
 * Author: chris grzegorczyk <grze@eucalyptus.com>
 */
package com.eucalyptus.util;

import org.apache.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.criterion.*;
import org.hibernate.ejb.EntityManagerFactoryImpl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import javax.persistence.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class EntityWrapper<TYPE> {

  private static Logger                            LOG = Logger.getLogger( EntityWrapper.class );

  private static Map<String, EntityManagerFactory> emf = new ConcurrentSkipListMap<String, EntityManagerFactory>( );

  public static EntityManagerFactory getEntityManagerFactory( ) {
    return EntityWrapper.getEntityManagerFactory( "eucalyptus" );
  }

  @SuppressWarnings( "deprecation" )
  public static EntityManagerFactory getEntityManagerFactory( final String persistenceContext ) {
    synchronized ( EntityWrapper.class ) {
      if ( !emf.containsKey( persistenceContext ) ) {
        LOG.info( "-> Setting up persistence context for : " + persistenceContext );
        LOG.info( "-> database host: " + System.getProperty( "euca.db.host" ) );
        LOG.info( "-> database port: " + System.getProperty( "euca.db.port" ) );
        emf.put( persistenceContext, Persistence.createEntityManagerFactory( persistenceContext ) );
      }
      return emf.get( persistenceContext );
    }
  }

  private EntityManager     em;
  private Session           session;
  private EntityTransaction tx;
  private boolean           doDebug = "DEBUG".equals( System.getProperty( "euca.log.level" ) );

  public EntityWrapper( ) {
    this( "eucalyptus" );
  }

  public EntityWrapper( String persistenceContext ) {
    if ( doDebug ) {
      Exception e = new Exception( );
      e.fillInStackTrace( );
      for ( StackTraceElement ste : e.getStackTrace( ) ) {
        if ( ste.getClassName( ).equals( EntityWrapper.class.getCanonicalName( ) ) || ste.getMethodName( ).matches( "getEntityWrapper" ) ) {
          continue;
        } else {
          LOG.debug( "CREATE CONNECTION " + ste.toString( ) );
          break;
        }
      }
    }
    EntityManagerFactoryImpl anemf = ( EntityManagerFactoryImpl ) EntityWrapper.getEntityManagerFactory( persistenceContext );
    if ( doDebug ) {
      anemf.getSessionFactory( ).getStatistics( ).setStatisticsEnabled( true );
      LOG.debug( "Hibernate statistics: " + anemf.getSessionFactory( ).getStatistics( ) );
    }
    this.em = anemf.createEntityManager( );
    this.session = ( Session ) em.getDelegate( );
    this.tx = em.getTransaction( );
    tx.begin( );
  }

  @SuppressWarnings( "unchecked" )
  public List<TYPE> query( TYPE example ) {
    LOG.debug( "QUERY " + example.getClass( ) );
    Example qbe = Example.create( example ).enableLike( MatchMode.EXACT );
    List<TYPE> resultList = ( List<TYPE> ) session.createCriteria( example.getClass( ) ).add( qbe ).list( );
    return Lists.newArrayList( Sets.newHashSet( resultList ) );
  }

  public TYPE getUnique( TYPE example ) throws EucalyptusCloudException {
    LOG.debug( "GET-UNIQUE " + example.getClass( ) );
    List<TYPE> res = this.query( example );
    if ( res.size( ) != 1 ) {
      String msg = null;
      msg = example.toString( );
      if ( msg != null && msg.startsWith( example.getClass( ).getCanonicalName( ) ) ) {
        msg = LogUtil.dumpObject( example );
      }
      throw new EucalyptusCloudException( "Error locating information for " + msg );
    }
    return res.get( 0 );
  }

  public void add( TYPE newObject ) {
    em.persist( newObject );
  }

  public void merge( TYPE newObject ) {
    em.merge( newObject );
  }

  public void mergeAndCommit( TYPE newObject ) {
    em.merge( newObject );
    this.commit( );
  }

  public void delete( TYPE deleteObject ) {
    em.remove( deleteObject );
  }

  public void rollback( ) {
    if( doDebug ) {
      Exception e = new Exception( );
      e.fillInStackTrace( );
      for ( StackTraceElement ste : e.getStackTrace( ) ) {
        if ( ste.getClassName( ).equals( EntityWrapper.class.getCanonicalName( ) ) ) {
          continue;
        } else {
          LOG.debug( "CLOSE CONNECTION " + ste.toString( ) );
          break;
        }
      }
    }
    if ( this.tx.isActive( ) ) {
      this.tx.rollback( );
    }
    this.em.close( );
  }

  public void commit( ) {
    if( doDebug ) {
      Exception e = new Exception( );
      e.fillInStackTrace( );
      for ( StackTraceElement ste : e.getStackTrace( ) ) {
        if ( ste.getClassName( ).equals( EntityWrapper.class.getCanonicalName( ) ) ) {
          continue;
        } else {
          LOG.debug( "CLOSE CONNECTION " + ste.toString( ) );
          break;
        }
      }
    }
    try {
      this.em.flush( );
      this.tx.commit( );
      this.em.close( );
    } catch ( Throwable e1 ) {
      LOG.trace( e1, e1 );
    }
  }

  @SuppressWarnings( "unchecked" )
  public <NEWTYPE> EntityWrapper<NEWTYPE> recast( Class<NEWTYPE> c ) {
    return ( EntityWrapper<NEWTYPE> ) this;
  }

  public EntityManager getEntityManager( ) {
    return em;
  }

  public Session getSession( ) {
    return session;
  }

}
