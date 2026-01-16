package com.jd.oxygent.core.oxygent.oxy.bank_tools;

import com.jd.oxygent.core.oxygent.oxy.BaseTool;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyRequest;
import com.jd.oxygent.core.oxygent.schemas.oxy.OxyResponse;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * BaseBank is a base class for bank implementations in the OxyGent framework.
 * Banks are specialized tools for managing user profiles, knowledge bases, and other
 * persistent data storage functionalities within the Multi-Agent System.
 *
 * <p>This class provides:
 * <ul>
 *   <li>Base structure for bank implementations</li>
 *   <li>Common functionality for data storage and retrieval</li>
 *   <li>Integration with the OxyGent tool ecosystem</li>
 * </ul>
 *
 * @author OxyGent Team
 * @version 1.0
 * @since 1.0
 */
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@Slf4j
public abstract class BaseBank extends BaseTool {

    /**
     * Bank category identifier.
     * Default value is "bank" as specified in the Python version.
     */
    @Builder.Default
    private String category = "bank";

    /**
     * Executes the bank operation with the provided request.
     * This is an abstract method that must be implemented by concrete bank classes.
     *
     * @param oxyRequest the request containing arguments for bank operation
     * @return the response containing the operation result
     */
    @Override
    protected OxyResponse _execute(OxyRequest oxyRequest) {
        throw new UnsupportedOperationException("This method is not yet implemented");
    }
}