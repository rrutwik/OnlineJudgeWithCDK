package com.myorg;

import java.util.Arrays;
import lombok.Getter;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.SecretValue;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.VpcProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ClusterProps;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.DatabaseInstanceProps;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;

@Getter
public class VpcStack extends Stack {

  private final Cluster cluster;
  private final String dbHost;
  private final String dbPort;
  private final String rdHost;
  private final String rdPort;

  public VpcStack(final Construct scope, final String id, final StackProps props) {
    super(scope, id, props);
    final Vpc vpc = new Vpc(this, "thisVpc", getVpcProps());
    this.cluster = new Cluster(this, "newCluster",
        ClusterProps.builder()
            .containerInsights(true)
            .clusterName("OJCluster")
            .vpc(vpc)
            .build());
    final DatabaseInstance databaseInstance = new DatabaseInstance(this, "thisDatabaseInstance",
        DatabaseInstanceProps.builder()
            .databaseName("onlinejudge")
            .credentials(
                Credentials.fromPassword("onlinejudge",
                    SecretValue.plainText("onlinejudge")))
            .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
                .version(PostgresEngineVersion.VER_12_4)
                .build()))
            .multiAz(true)
            .monitoringInterval(Duration.seconds(10))
            .instanceType(InstanceType.of(InstanceClass.BURSTABLE3, InstanceSize.MICRO))
            .vpc(vpc)
            .build());
    databaseInstance.getConnections().allowDefaultPortFromAnyIpv4();
//    final Instance instance = new Instance(this, "test", InstanceProps
//        .builder()
//        .instanceType(
//            InstanceType.of(InstanceClass.MEMORY5_NVME_DRIVE_HIGH_PERFORMANCE, InstanceSize.XLARGE))
//        .vpc(vpc)
//        .build());
//    final CfnCacheCluster cfnCacheCluster = new CfnCacheCluster(this, "cachecluster",
//        CfnCacheClusterProps.builder()
//            .engine("redis")
//            .clusterName(cluster.getClusterName())
//            .cacheNodeType("cache.t1.micro")
//            .numCacheNodes(1)
//            .vpcSecurityGroupIds(Arrays.asList(vpc.getVpcDefaultSecurityGroup()))
//            .build());
    this.dbHost = databaseInstance.getDbInstanceEndpointAddress();
    this.dbPort = databaseInstance.getDbInstanceEndpointPort();
    this.rdHost = "127.0.0.1";
    this.rdPort = "6379";
  }

  private VpcProps getVpcProps() {
    return VpcProps.builder()
        .maxAzs(2)
        .build();
  }

  private VpcProps getVpcProps2() {
    return VpcProps.builder()
        // CIDR and number of ip addresses
        // https://network00.com/NetworkTools/IPv4AddressPlanner/
        // https://cloud.ibm.com/docs/vpc-on-classic-network?topic=vpc-on-classic-network-choosing-ip-ranges-for-your-vpc
        .cidr("10.0.0.0/16")
        .natGateways(0)
        .subnetConfiguration(Arrays.asList(
            SubnetConfiguration.builder()
                .cidrMask(24)
                .name("aurora_isolated")
                .subnetType(SubnetType.ISOLATED)
                .build(),
            SubnetConfiguration.builder()
                .cidrMask(24)
                .name("redis_isolated")
                .subnetType(SubnetType.ISOLATED)
                .build(),
            SubnetConfiguration.builder()
                .cidrMask(24)
                .name("es_isolated")
                .subnetType(SubnetType.ISOLATED)
                .build(),
            SubnetConfiguration.builder()
                .cidrMask(24)
                .name("fargate_cluster")
                .subnetType(SubnetType.PUBLIC)
                .build()
        ))
        .maxAzs(1)
        .build();
  }
}
