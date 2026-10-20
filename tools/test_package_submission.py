"""Regression checks use generated sentinel strings, never real credentials."""
import unittest
from package_submission import excluded, scan


class SubmissionSafetyTest(unittest.TestCase):
    def test_local_configuration_and_generated_outputs_are_excluded(self):
        for name in ('local.properties', '.git/config', '.gradle/cache',
                     'app/build/generated/BuildConfig.java', 'signing.properties',
                     'release.jks', '.env.local', '.idea/workspace.xml', '../outside'):
            with self.subTest(name=name):
                self.assertTrue(excluded(name))

    def test_source_tests_and_wrapper_jar_are_kept(self):
        for name in ('app/src/main/App.kt', 'app/src/test/ExampleTest.kt',
                     'gradle/wrapper/gradle-wrapper.jar', 'local.properties.example'):
            with self.subTest(name=name):
                self.assertFalse(excluded(name))

    def test_variable_reference_is_not_a_credential(self):
        scan('AuthInterceptor.kt', b'val apiKey = BuildConfig.TMDB_API_KEY', [])

    def test_only_placeholders_are_allowed_in_example(self):
        scan('local.properties.example', b'TMDB_API_KEY=your_tmdb_api_key_here', [])
        with self.assertRaises(ValueError):
            scan('local.properties.example', b'TMDB_API_KEY=not-a-placeholder', [])

    def test_known_local_value_is_detected_without_printing_it(self):
        sentinel = b'unit-test-' + b'x' * 32
        with self.assertRaises(ValueError) as caught:
            scan('fixture.txt', b'prefix ' + sentinel, [sentinel])
        self.assertNotIn(sentinel.decode(), str(caught.exception))

    def test_long_literal_credential_assignment_is_detected(self):
        with self.assertRaises(ValueError):
            scan('fixture.kt', b'val apiKey = "' + b'x' * 32 + b'"', [])

    def test_token_signature_is_detected(self):
        with self.assertRaises(ValueError):
            scan('fixture.txt', b'gh' + b'p_' + b'x' * 36, [])

    def test_machine_specific_example_configuration_is_rejected(self):
        with self.assertRaises(ValueError):
            scan('local.properties.example', b'sdk.dir=/machine/sdk', [])


if __name__ == '__main__':
    unittest.main()
