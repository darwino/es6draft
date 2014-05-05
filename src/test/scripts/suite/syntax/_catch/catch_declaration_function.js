/*
 * Copyright (c) 2012-2014 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */

const {
  assertSyntaxError
} = Assert;

// Catch variable is BindingIdentifier
assertSyntaxError(`try {} catch (e) { function e(){} }`);
assertSyntaxError(`try {} catch (e) { function* e(){} }`);

assertSyntaxError(`try {} catch (e) { function e(){} }`);
assertSyntaxError(`try {} catch (e) { function* e(){} }`);


// Catch variable is ArrayBindingPattern
assertSyntaxError(`try {} catch ([e]) { function e(){} }`);
assertSyntaxError(`try {} catch ([e]) { function* e(){} }`);

assertSyntaxError(`try {} catch ([e]) { function e(){} }`);
assertSyntaxError(`try {} catch ([e]) { function* e(){} }`);


// Catch variable is ObjectBindingPattern
assertSyntaxError(`try {} catch ({e}) { function e(){} }`);
assertSyntaxError(`try {} catch ({e}) { function* e(){} }`);

assertSyntaxError(`try {} catch ({e}) { function e(){} }`);
assertSyntaxError(`try {} catch ({e}) { function* e(){} }`);