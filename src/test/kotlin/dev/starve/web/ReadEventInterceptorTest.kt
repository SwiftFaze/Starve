package dev.starve.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.method.HandlerMethod

class ReadEventInterceptorTest {
    private val handlers = Handlers()

    private fun headerAfter(handler: Any): String? {
        val response = MockHttpServletResponse()
        ReadEventInterceptor().postHandle(MockHttpServletRequest(), response, handler, null)
        return response.getHeader(HX_TRIGGER)
    }

    private fun method(name: String) = HandlerMethod(handlers, Handlers::class.java.getMethod(name))

    @Test
    fun `a handler that announces a read raises the read event`() {
        assertEquals(READ_EVENT, headerAfter(method("reads")))
    }

    @Test
    fun `other handlers raise nothing`() {
        assertNull(headerAfter(method("plain")))
        assertNull(headerAfter("a resource handler"))
    }

    class Handlers {
        @AnnouncesRead
        fun reads() = Unit

        fun plain() = Unit
    }
}
