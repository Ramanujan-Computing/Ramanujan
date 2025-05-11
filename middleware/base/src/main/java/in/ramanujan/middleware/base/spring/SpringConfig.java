package in.ramanujan.middleware.base.spring;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"in.ramanujan.*", "in.ramanujan.db.layer."})
public class SpringConfig {
}
