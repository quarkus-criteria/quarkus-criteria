package com.github.quarkus.criteria;

import io.quarkus.test.junit.NativeImageTest;
import org.junit.jupiter.api.Disabled;

@NativeImageTest
@Disabled("H2 is not supported in native mode. TODO: switch to postgres with test containers")
public class CrudServiceNativeIt extends CrudServiceIt {
}
