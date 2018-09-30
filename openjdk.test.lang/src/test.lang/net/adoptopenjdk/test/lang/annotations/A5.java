/*******************************************************************************
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

package net.adoptopenjdk.test.lang.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.Documented;
import java.lang.annotation.RetentionPolicy;

@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface A5
{
	public enum Priority { LOW, MEDIUM, HIGH }
	String value() default "";
	String[] changedBy() default "";
	String[] lastChangedBy() default "";
	Priority priority() default Priority.MEDIUM;
	String createdBy() default "Bob Bobson";
	String lastChanged() default "14/10/2010";
 }
