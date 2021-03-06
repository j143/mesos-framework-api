package com.github.kevints.mesos.scheduler.server;

import com.github.kevints.libprocess.client.LibprocessClientBuilder;
import com.github.kevints.libprocess.client.PID;
import com.github.kevints.mesos.master.client.MesosMasterClient;
import com.github.kevints.mesos.messages.gen.Mesos.Credential;
import com.github.kevints.mesos.messages.gen.Mesos.FrameworkInfo;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;

import static java.util.Objects.requireNonNull;

public class MesosSchedulerServerImpl implements MesosSchedulerServer {
  private final Scheduler scheduler;
  private final MesosMasterClient master;

  private final FrameworkInfo frameworkInfo;
  private final Credential credential;
  private final ListeningExecutorService serverExecutor;

  private final Server masterServer;
  private final int serverPort;
  private final AuthenticateeServlet authenticateeServlet;

  MesosSchedulerServerImpl(MesosSchedulerServerBuilder builder, Scheduler scheduler, MesosMasterClient master, FrameworkInfo frameworkInfo) {
    this.credential = requireNonNull(builder.getCredential());
    this.serverExecutor = requireNonNull(builder.getServerExecutor());
    this.serverPort = builder.getServerPort();
    this.scheduler = requireNonNull(scheduler);
    this.master = requireNonNull(master);
    this.frameworkInfo = requireNonNull(frameworkInfo);

    Server schedulerProcessServer = new Server(new ExecutorThreadPool(builder.getServerExecutor()));

    org.eclipse.jetty.util.thread.Scheduler taskScheduler =
        new ScheduledExecutorScheduler("Jetty-Scheduler", true);

    // This is necessary for the session manager and connection timeout logic to use non-daemon
    // threads.
    schedulerProcessServer.addBean(taskScheduler);

    ServerConnector connector = new ServerConnector(schedulerProcessServer);

    connector.setHost(requireNonNull(builder.getServerHostAddress()));
    connector.setPort(serverPort);
    schedulerProcessServer.addConnector(connector);

    ServletContextHandler context = new ServletContextHandler();
    context.setContextPath("/");
    context.addServlet(new ServletHolder(
        new SchedulerServlet(scheduler, serverExecutor, master)),
        "/scheduler(1)/*");

    authenticateeServlet = new AuthenticateeServlet(
        PID.fromString("authenticatee@127.0.0.1:8080"),
        credential,
        new LibprocessClientBuilder()
            .setFromPort(8080)
            .setFromId("authenticatee")
            .build());
    context.addServlet(new ServletHolder(authenticateeServlet), "/authenticatee/*");

    schedulerProcessServer.setHandler(context);
    this.masterServer = schedulerProcessServer;
  }

  @Override
  public LifeCycle getServerLifeCycle() {
    return masterServer;
  }

  @Override
  public AuthenticateeServlet getAuthenticateeServlet() {
    return authenticateeServlet;
  }
}
