package com.gihub.quarkus.criteria.deployment;

import com.github.quarkus.criteria.runtime.criteria.BaseCriteriaSupport;
import com.github.quarkus.criteria.runtime.criteria.ExampleBuilder;
import com.github.quarkus.criteria.runtime.service.CrudService;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.deployment.BeanRegistrationPhaseBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.apache.deltaspike.data.impl.RepositoryExtension;
import org.apache.deltaspike.data.impl.tx.ThreadLocalEntityManagerHolder;
import org.apache.deltaspike.jpa.impl.entitymanager.DefaultEntityManagerHolder;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;

import javax.enterprise.inject.Vetoed;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class QuarkusCriteriaProcessor {

    private static final List<String> BEANS_TO_VETO = Arrays.asList("org.apache.deltaspike.jpa.impl.entitymanager.DefaultEntityManagerHolder",
            "org.apache.deltaspike.data.impl.RepositoryExtension", "org.apache.deltaspike.jpa.impl.entitymanager.EntityManagerRefLookup",
            "org.apache.deltaspike.jpa.impl.transaction.TransactionalInterceptor", "org.apache.deltaspike.jpa.spi.transaction.TransactionStrategy",
            "org.apache.deltaspike.jpa.impl.transaction.ResourceLocalTransactionStrategy", "org.apache.deltaspike.core.spi.InterceptorStrategy",
            "org.apache.deltaspike.data.impl.handler.QueryHandler", "org.apache.deltaspike.data.impl.meta.",
            "org.apache.deltaspike.core.impl.scope.", "org.apache.deltaspike.data.impl.tx.");

    @BuildStep
    void buildFeature(BuildProducer<FeatureBuildItem> feature) throws IOException {
        feature.produce(new FeatureBuildItem("quarkus-criteria"));
    }

    @BuildStep
    void removeBeans(BeanRegistrationPhaseBuildItem beanRegistrationPhase) {
        beanRegistrationPhase.getContext()
                .configure(RepositoryExtension.class).addQualifier(Vetoed.class).done();
        beanRegistrationPhase.getContext()
                .configure(DefaultEntityManagerHolder.class).addQualifier(Vetoed.class).done();
        beanRegistrationPhase.getContext()
                .configure(ThreadLocalEntityManagerHolder.class).addQualifier(Vetoed.class).done();
    }

    @BuildStep
    void buildCdiBeans(BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformerBuildItemBuildProducer) {

        annotationsTransformerBuildItemBuildProducer.produce(new AnnotationsTransformerBuildItem(new AnnotationsTransformer() {
            public boolean appliesTo(org.jboss.jandex.AnnotationTarget.Kind kind) {
                return kind == org.jboss.jandex.AnnotationTarget.Kind.CLASS;
            }

            public void transform(AnnotationsTransformer.TransformationContext context) {
                BEANS_TO_VETO.
                        forEach(toVeto -> {
                            if (context.getTarget().asClass().name().toString().startsWith(toVeto)) {
                                context.transform().add(Vetoed.class).done();
                            }
                        });
            }
        }));
    }

    @BuildStep
    void registerForReflection(BuildProducer<ReflectiveClassBuildItem> reflectiveClassBuildItemProducer,
                               CombinedIndexBuildItem combinedIndex) {

        List<String> classesToRegisterForReflection = combinedIndex.getIndex()
                .getAllKnownSubclasses(DotName.createSimple(BaseCriteriaSupport.class.getName()))
                .stream()
                .map(ClassInfo::toString)
                .collect(Collectors.toList());

        classesToRegisterForReflection.addAll(Arrays.asList(BaseCriteriaSupport.class.getName(), CrudService.class.getName(),
                ExampleBuilder.class.getName(), ExampleBuilder.ExampleBuilderDsl.class.getName()));
        reflectiveClassBuildItemProducer.produce(
                new ReflectiveClassBuildItem(false, false, classesToRegisterForReflection
                        .toArray(new String[classesToRegisterForReflection.size()])));
    }

}
