package com.eucalyptus.reporting.queue;

import java.util.ArrayList;
import java.util.List;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.log4j.*;

public class QueueBroker
{
	private static Logger log = Logger.getLogger( QueueBroker.class );

	public static final String DEFAULT_NAME = "reportingBroker";
	public static final String DEFAULT_DIR  = "/tmp";
	public static final String DEFAULT_URL  = "tcp://localhost:63636";
		
	private static QueueBroker instance = null;

	public static QueueBroker getInstance()
	{
		if (instance == null) {
			instance = new QueueBroker(DEFAULT_NAME, DEFAULT_URL, DEFAULT_DIR);
		}
		return instance;
	}
	
	private boolean started = false;

	private String brokerName;
	private String brokerDataDir;
	private String brokerUrl;
	
	private List<ActiveMQDestination> destinations;

	private BrokerService brokerService;
	private JmsBrokerThread brokerThread;

	private QueueBroker(String brokerName, String brokerUrl, String brokerDataDir)
	{
		this.brokerName = brokerName;
		this.brokerUrl = brokerUrl;
		this.brokerDataDir = brokerDataDir;
		this.destinations = new ArrayList<ActiveMQDestination>();
	}
	
	public void addDestination(String destName)
	{
		ActiveMQDestination dest =
			ActiveMQDestination.createDestination(destName, ActiveMQDestination.QUEUE_TYPE);
		this.destinations.add(dest);
	}
	
	public void startup()
	{
		try {
			brokerService = new BrokerService();
			brokerService.setBrokerName(brokerName);
			brokerService.setDataDirectory(brokerDataDir);
			brokerService.addConnector(brokerUrl);
			ActiveMQDestination[] dests = new ActiveMQDestination[destinations.size()];
			destinations.toArray(dests);
			brokerService.setDestinations(dests);
			brokerThread = new JmsBrokerThread(brokerService);
			brokerThread.start();
			Thread.sleep(1000); // give the broker a moment to startup; TODO:
								// fix this
			if (brokerThread.getStartException() != null) {
				throw brokerThread.getStartException();
			}
		} catch (Exception ex) {
			throw new QueueRuntimeException(ex);
		}
		log.info("Broker started");
	}

	public void shutdown()
	{
		try {
			brokerService.stop();
		} catch (Exception ex) {
			throw new QueueRuntimeException(ex);
		}
		log.info("Broker stopped");
	}

	public String getBrokerName()
	{
		return this.brokerName;
	}

	public String getBrokerUrl()
	{
		return this.brokerUrl;
	}

	public static void main(String[] args)
		throws Exception
	{
		QueueBroker broker = new QueueBroker(DEFAULT_NAME, DEFAULT_DIR, DEFAULT_URL);
		broker.startup();
	}


	/**
	 * The JMS broker must run in a separate thread if it's embedded because
	 * brokerService.start() never returns.
	 */
	private static class JmsBrokerThread
		extends Thread
	{
		private static Logger log = Logger.getLogger( JmsBrokerThread.class );

		private final BrokerService brokerService;
		private Exception exception = null;

		JmsBrokerThread(final BrokerService brokerService)
		{
			this.brokerService = brokerService;
		}

		public void run()
		{
			try {
				brokerService.start();
			} catch (Exception ex) {
				log.error(ex);
				exception = ex;
			}
		}

		public Exception getStartException()
		{
			return exception;
		}

	}

}
